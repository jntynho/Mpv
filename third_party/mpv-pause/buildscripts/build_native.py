from pathlib import Path
import os,sys,subprocess,re,shutil
r=Path('/build');abi=sys.argv[1]
assert abi in ['arm64-v8a','armeabi-v7a']
arch='aarch64' if abi=='arm64-v8a' else 'arm'
triple='aarch64-linux-android' if arch=='aarch64' else 'armv7a-linux-androideabi'
ndk=Path('/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin')
cc=str(ndk/(triple+'21-clang'));cxx=cc+'++';ar=str(ndk/'llvm-ar');ranlib=str(ndk/'llvm-ranlib')
prefix=r/'prefix'/abi;prefix.mkdir(parents=True,exist_ok=True)
work=r/'work'/abi;work.mkdir(parents=True,exist_ok=True)
env=dict(os.environ);env['PKG_CONFIG_LIBDIR']=str(prefix/'lib/pkgconfig');env['PKG_CONFIG_SYSROOT_DIR']='';env.pop('PKG_CONFIG_PATH',None)
env.update({'CC':cc,'CXX':cxx,'AR':ar,'RANLIB':ranlib,'STRIP':str(ndk/'llvm-strip'),'CFLAGS':'-O2 -fPIC','CXXFLAGS':'-O2 -fPIC','LDFLAGS':'-Wl,-z,max-page-size=16384'})
(prefix/'lib/pkgconfig').mkdir(parents=True,exist_ok=True)
for f in (r/'original'/abi).glob('*.so'):
 if f.name!='libmpv.so':shutil.copy2(f,prefix/'lib'/f.name)
cross=work/'cross.ini'
cross.write_text(f'''[binaries]
c = '{cc}'
cpp = '{cxx}'
ar = '{ar}'
strip = '{ndk / 'llvm-strip'}'
pkgconfig = '/usr/bin/pkg-config'
[host_machine]
system = 'android'
cpu_family = '{arch}'
cpu = '{'armv8-a' if arch=='aarch64' else 'armv7-a'}'
endian = 'little'
[properties]
needs_exe_wrapper = true
[built-in options]
c_args = ['-O2', '-fPIC']
cpp_args = ['-O2', '-fPIC']
c_link_args = ['-Wl,-z,max-page-size=16384']
cpp_link_args = ['-Wl,-z,max-page-size=16384']
''')
def run(args,cwd=None):
 print('+', ' '.join(str(x) for x in args),flush=True)
 subprocess.run([str(x) for x in args],cwd=cwd,env=env,check=True)
def meson(name,extra):
 b=work/name;src=r/'src'/name
 if not (b/'build.ninja').exists():
  run(['meson','setup',b,src,'--cross-file',cross,'--prefix',prefix,'--libdir','lib','--buildtype','release','--default-library','static',*extra])
 run(['ninja','-C',b,'-j2']);run(['ninja','-C',b,'install'])
# Configure, but do not rebuild or replace FFmpeg. Public headers must match the exact bundled ABI.
b=work/'ffmpeg';b.mkdir(exist_ok=True)
if not (b/'config.h').exists():
 run([r/'src/ffmpeg/configure',f'--prefix={prefix}','--target-os=android',f'--arch={arch}',f'--cc={cc}',f'--cxx={cxx}',f'--ar={ar}',f'--ranlib={ranlib}',f'--nm={ndk/"llvm-nm"}',f'--strip={ndk/"llvm-strip"}','--enable-cross-compile','--enable-shared','--disable-static','--disable-programs','--disable-doc','--disable-everything','--disable-autodetect','--disable-asm'],cwd=b)
run(['make','install-headers'],cwd=b)
for lib in ['libavcodec','libavfilter','libavformat','libavutil','libavdevice','libswresample','libswscale']:
 s='\n'.join(p.read_text() for p in (r/'src/ffmpeg'/lib).glob('version*.h'))
 stem=lib.upper();version='.'.join(re.search(r'#define\s+'+stem+'_VERSION_'+part+r'\s+(\d+)',s).group(1) for part in ['MAJOR','MINOR','MICRO'])
 (prefix/'lib/pkgconfig'/(lib+'.pc')).write_text(f'prefix={prefix}\nlibdir=${{prefix}}/lib\nincludedir=${{prefix}}/include\nName: {lib}\nDescription: Exact upstream FFmpeg headers, bundled unmodified shared library\nVersion: {version}\nLibs: -L${{libdir}} -l{lib[3:]}\nCflags: -I${{includedir}}\n')
meson('freetype',['-Dharfbuzz=disabled','-Dbzip2=disabled','-Dbrotli=disabled','-Dpng=disabled','-Dtests=disabled'])
meson('fribidi',['-Ddocs=false','-Dbin=false','-Dtests=false'])
meson('harfbuzz',['-Dtests=disabled','-Ddocs=disabled','-Dglib=disabled','-Dgobject=disabled','-Dcairo=disabled','-Dicu=disabled'])
b=work/'unibreak';b.mkdir(exist_ok=True)
if not (b/'Makefile').exists():run([r/'src/unibreak/configure',f'--prefix={prefix}',f'--host={triple}','--enable-static','--disable-shared','--with-pic'],cwd=b)
run(['make','-j2'],cwd=b);run(['make','install'],cwd=b)
b=work/'lua'
if not b.exists():shutil.copytree(r/'src/lua',b)
run(['make','-C',b/'src','clean'])
run(['make','-C',b/'src','liblua.a','-j2',f'CC={cc}',f'AR={ar} rcu',f'RANLIB={ranlib}','MYCFLAGS=-fPIC -DLUA_USE_POSIX -DLUA_USE_DLOPEN -Dlua_fseek -Dgetlocaledecpoint\\(\\)=\\(46\\)'])
(prefix/'include/lua5.2').mkdir(parents=True,exist_ok=True)
for n in ['lua.h','lualib.h','lauxlib.h','luaconf.h']:shutil.copy2(b/'src'/n,prefix/'include/lua5.2'/n)
shutil.copy2(b/'src/liblua.a',prefix/'lib/liblua5.2.a')
(prefix/'lib/pkgconfig/lua5.2.pc').write_text(f'prefix={prefix}\nName: Lua\nDescription: Lua 5.2.4\nVersion: 5.2.4\nLibs: -L${{prefix}}/lib -llua5.2 -lm -ldl\nCflags: -I${{prefix}}/include/lua5.2\n')
meson('libass',['-Drequire-system-font-provider=false','-Dfontconfig=disabled','-Ddirectwrite=disabled','-Dcoretext=disabled','-Dlibunibreak=enabled','-Dtest=disabled','-Dcompare=disabled','-Dprofile=disabled','-Dcheckasm=disabled'])
meson('libplacebo',['-Dvulkan=disabled','-Ddemos=false','-Dtests=false','-Dopengl=enabled','-Dglslang=disabled','-Dshaderc=disabled','-Dlcms=disabled','-Dlibdovi=disabled'])
p=prefix/'lib/pkgconfig/libplacebo.pc';s=p.read_text();s=re.sub(r'^(Libs:.*)$',r'\1 -lc++_shared',s,flags=re.M);p.write_text(s)
b=work/'mpv'
if not (b/'build.ninja').exists():
 run(['meson','setup',b,r/'src/mpv','--cross-file',cross,'--prefix',prefix,'--libdir','lib','--buildtype','release','--default-library','shared','-Db_lundef=true','-Diconv=disabled','-Dlua=enabled','-Dlibmpv=true','-Dcplayer=false','-Dmanpage-build=disabled','-Dopensles=enabled','-Dandroid-media-ndk=enabled','-Degl-android=enabled','-Dgl=enabled'])
run(['ninja','-C',b,'-j2'])
output=r/'output'/abi;output.mkdir(parents=True,exist_ok=True)
shutil.copy2(b/'libmpv.so',output/'libmpv.so')
run([ndk/'llvm-strip','--strip-unneeded',output/'libmpv.so'])
print('COMPLETED',abi,(output/'libmpv.so').stat().st_size,flush=True)
