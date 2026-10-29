#!/usr/bin/env python3
"""Exercise original mpv AO code and the OpenSLES patch using a simulated device/clock.

This is a native regression test, not Android audio/GPU measurement. Both ARM
libraries are additionally built from the full patched source, not this harness.
"""
from pathlib import Path
import subprocess
import tempfile
import shutil
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / 'third_party/mpv-pause'

# Verify that the tested source patch and packaged ARM binaries are the recorded inputs.
provenance = json.loads((NATIVE/'provenance.json').read_text())
assert hashlib.sha256((NATIVE/'opensles-pause.patch').read_bytes()).hexdigest() == provenance['patchSha256']
assert hashlib.sha256((NATIVE/'buildscripts/sources.lock.json').read_bytes()).hexdigest() == provenance['sourceLockSha256']
assert hashlib.sha256((NATIVE/'buildscripts/build_native.py').read_bytes()).hexdigest() == provenance['buildScriptSha256']
for name, expected in provenance['fixtures'].items():
    assert hashlib.sha256((NATIVE/name).read_bytes()).hexdigest() == expected, name
for abi, entry in provenance['binaries'].items():
    data = (NATIVE/'native'/abi/'libmpv.so').read_bytes()
    assert hashlib.sha256(data).hexdigest() == entry['sha256'], abi
    assert len(data) == entry['bytes']
    assert data[:4] == b'\x7fELF' and data[5] == 1
    assert struct.unpack_from('<H', data, 16)[0] == 3
    assert struct.unpack_from('<H', data, 18)[0] == (183 if abi == 'arm64-v8a' else 40)
    assert b'mpvkt-pause1' in data and b'Failed to change native pause state' in data
    if abi == 'arm64-v8a':
        offset = struct.unpack_from('<Q', data, 32)[0]
        size, count = struct.unpack_from('<HH', data, 54)
        alignments = [struct.unpack_from('<Q', data, offset+i*size+48)[0]
                      for i in range(count) if struct.unpack_from('<I', data, offset+i*size)[0] == 1]
        assert alignments and min(alignments) >= 16384
    print('Verified native provenance and ELF:', abi, flush=True)


def extract(source, signature):
    start = source.index(signature)
    start = source.rfind('\n', 0, start) + 1
    index = source.index('{', start) + 1
    depth = 1
    while depth:
        if source[index] == '{':
            depth += 1
        elif source[index] == '}':
            depth -= 1
        index += 1
    return source[start:index]


PREFIX = r'''
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <pthread.h>
#include <math.h>
#define MPMAX(a,b) ((a)>(b)?(a):(b))
#define MP_TIME_NS_TO_S(a) ((double)(a)/1e9)
#define SL_RESULT_SUCCESS 0
#define SL_PLAYSTATE_PAUSED 2
#define SL_PLAYSTATE_PLAYING 3
#define MP_ERR(...) ((void)0)
typedef int SLresult;
typedef unsigned SLuint32;
struct SLPlayItf_; struct SLBufferQueueItf_;
typedef struct SLPlayItf_ *const *SLPlayItf;
typedef struct SLBufferQueueItf_ *const *SLBufferQueueItf;
struct SLPlayItf_ { SLresult (*SetPlayState)(SLPlayItf,SLuint32); };
struct SLBufferQueueItf_ { SLresult (*Clear)(SLBufferQueueItf); };
struct priv { SLPlayItf play; SLBufferQueueItf buffer_queue; };
struct mp_pcm_state { double delay; };
struct buffer_state {
    pthread_mutex_t lock;
    bool playing, paused, streaming, recover_pause, hw_paused;
    struct mp_pcm_state prepause_state;
    int64_t queued_time_ns, end_time_ns;
    void *queue, *pending, *filter_root;
};
struct ao;
struct driver {
    void *write;
    void (*reset)(struct ao*);
    void (*start)(struct ao*);
    bool (*set_pause)(struct ao*,bool);
};
struct ao { struct buffer_state *buffer_state; struct driver *driver;
    struct priv *priv; bool stream_silence,untimed; int samplerate; };
static int64_t now;
static int clears, transitions, reloads, hardware_samples, hardware_state, fail_state;
static struct ao *active;
static int64_t mp_time_ns(void) { return now; }
#define mp_mutex_lock pthread_mutex_lock
#define mp_mutex_unlock pthread_mutex_unlock
static bool ao_is_playing(struct ao*a) { return a->buffer_state->playing; }
static void ao_drain(struct ao*a) { (void)a; }
static void ao_wakeup(struct ao*a) { (void)a; }
static void get_dev_state(struct ao*a,struct mp_pcm_state*s) { (void)a;s->delay=0; }
#define TA_FREEP(p) (*(p)=NULL)
static void mp_async_queue_reset(void*p) { (void)p; }
static void mp_async_queue_resume_reading(void*p) { (void)p; }
static void mp_filter_reset(void*p) { (void)p; }
static int64_t mp_async_queue_get_samples(void*p) { (void)p;return 0; }
static int mp_aframe_get_size(void*p) { (void)p;return 0; }
static void ao_request_reload(struct ao*a) { (void)a;reloads++; }
static void buffer_callback(SLBufferQueueItf q,void*a) { (void)q;(void)a;hardware_samples+=6000; }
static SLresult fake_clear(SLBufferQueueItf q) { (void)q;clears++;hardware_samples=0;return 0; }
static SLresult fake_state(SLPlayItf p,SLuint32 state) {
    (void)p;
    // A synchronous Android state change must never wait for a callback holding this lock.
    assert(pthread_mutex_trylock(&active->buffer_state->lock)==0);
    pthread_mutex_unlock(&active->buffer_state->lock);
    if(fail_state)return 1;
    transitions++;hardware_state=state;
    return 0;
}
'''

MAIN = r'''
int main(void) {
    const int waits[]={1,10,100,1000,20000};
    for(unsigned n=0;n<sizeof(waits)/sizeof(waits[0]);n++) {
        struct SLPlayItf_ play={fake_state};struct SLPlayItf_*play_table=&play;
        struct SLBufferQueueItf_ queue={fake_clear};struct SLBufferQueueItf_*queue_table=&queue;
        struct priv priv={&play_table,&queue_table};
        struct buffer_state p={.lock=PTHREAD_MUTEX_INITIALIZER,.playing=true,.streaming=true};
        struct driver d={.reset=reset,.start=resume,.set_pause=PATCHED?set_pause:NULL};
        struct ao a={.buffer_state=&p,.driver=&d,.priv=&priv,.samplerate=48000};
        active=&a;now=1000000000;clears=transitions=reloads=fail_state=0;
        hardware_samples=6000;hardware_state=SL_PLAYSTATE_PLAYING;
        p.end_time_ns=now+125000000;
        double initial=ao_get_delay(&a);
        ao_set_paused(&a,true,false);
        ao_set_paused(&a,true,false); // Idempotent pause must not duplicate native operations.
        now+=(int64_t)waits[n]*1000000;
        if(PATCHED) {
            assert(hardware_state==SL_PLAYSTATE_PAUSED);
            assert(hardware_samples==6000 && clears==0 && transitions==1);
            assert(fabs(ao_get_delay(&a)-initial)<1e-9); // Audio clock freezes throughout pause.
        } else {
            assert(clears==1 && hardware_samples==0);
        }
        ao_set_paused(&a,false,false);
        ao_set_paused(&a,false,false);
        double jump=(initial-ao_get_delay(&a))*1000;
        if(PATCHED) {
            assert(fabs(jump)<1e-6);
            assert(hardware_samples==6000 && clears==0 && transitions==2);
            assert(hardware_state==SL_PLAYSTATE_PLAYING);
            for(int k=0;k<100;k++) {
                ao_set_paused(&a,true,false);now+=20000000;
                assert(fabs(ao_get_delay(&a)-initial)<1e-9);
                ao_set_paused(&a,false,false);
                assert(fabs(ao_get_delay(&a)-initial)<1e-9);
            }
            // Seek while paused: real AO reset clears the old samples and hw_paused flag.
            // Starting it in paused mode must wait, then resume the OpenSL device and prime it.
            ao_set_paused(&a,true,false);
            ao_reset(&a);assert(clears==1 && hardware_samples==0);
            assert(!p.hw_paused && !p.playing && hardware_state==SL_PLAYSTATE_PAUSED);
            ao_start(&a);assert(p.playing && hardware_samples==0);
            ao_set_paused(&a,false,false);
            assert(hardware_state==SL_PLAYSTATE_PLAYING && hardware_samples==6000);
            // Seek/stop during playback must still restart and prime the device normally.
            ao_reset(&a);assert(clears==2 && hardware_samples==0);
            ao_start(&a);assert(hardware_state==SL_PLAYSTATE_PLAYING && hardware_samples==6000);
            // Unsupported native pause requests reload; it cannot silently claim driver success.
            fail_state=1;assert(!set_pause(&a,true));assert(reloads==1);
        } else {
            assert(jump>0 && clears==1);
        }
        printf("%s pause=%dms resume_clock_jump=%.3fms\n",PATCHED?"patched":"upstream",waits[n],jump);
        pthread_mutex_destroy(&p.lock);
    }
    return 0;
}
'''

with tempfile.TemporaryDirectory(prefix='mpv-native-pause-') as tmp:
    temp = Path(tmp)
    shutil.copytree(NATIVE/'upstream', temp/'patched')
    with (NATIVE/'opensles-pause.patch').open('rb') as patch:
        subprocess.run(['patch', '--batch', '--fuzz=0', '-p1', '-d', str(temp/'patched')], stdin=patch, check=True, stdout=subprocess.DEVNULL)
    backend = (temp/'patched/audio/out/ao_opensles.c').read_text()
    assert '.set_pause = set_pause,' in backend
    for patched in [False, True]:
        backend_source = (temp/'patched/audio/out/ao_opensles.c' if patched else NATIVE/'upstream/audio/out/ao_opensles.c').read_text()
        signatures = ['static void reset(', 'static bool set_pause(', 'static void resume('] if patched else ['static void reset(', 'static void resume(']
        backend_functions = '\n'.join(extract(backend_source, sig) for sig in signatures)
        if not patched:
            backend_functions += '\nstatic bool set_pause(struct ao*a,bool p) { (void)a;(void)p;return false; }\n'
        source = ((temp/'patched' if patched else NATIVE/'upstream')/'audio/out/buffer.c').read_text()
        functions = '\n'.join(extract(source, sig) for sig in ['double ao_get_delay(', 'void ao_reset(', 'void ao_start(', 'void ao_set_paused('])
        path = temp/('patched.c' if patched else 'upstream.c')
        path.write_text(f'#define PATCHED {int(patched)}\n'+PREFIX+backend_functions+functions+MAIN)
        exe = path.with_suffix('.test')
        subprocess.run(['cc','-std=c11','-O0','-pthread',str(path),'-o',str(exe)],check=True)
        subprocess.run([str(exe)],check=True)
print('PASS: upstream loss reproduced; patched queue/clock preserved, repeated toggles, paused/playing seeks, reset and error handling checked.')
