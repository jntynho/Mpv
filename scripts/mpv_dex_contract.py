"""Read the final DEX class data, not just name strings, to verify the JNI callback contract."""
import struct


def mpv_members(data):
    assert data.startswith(b'dex\n'), 'Unsupported DEX container'
    def u32(offset): return struct.unpack_from('<I', data, offset)[0]
    def uleb(offset):
        value = shift = 0
        while True:
            byte = data[offset]; offset += 1
            value |= (byte & 127) << shift
            if byte < 128: return value, offset
            shift += 7
            assert shift < 35
    count, offset = u32(56), u32(60)
    strings = []
    for i in range(count):
        start = u32(offset + i * 4)
        _, start = uleb(start)
        strings.append(data[start:data.index(b'\0', start)].decode('utf-8', errors='replace'))
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    prototypes = []
    for i in range(u32(72)):
        _, result, parameters = struct.unpack_from('<III', data, u32(76) + i * 12)
        arguments = [] if not parameters else [types[x] for x in struct.unpack_from('<' + 'H' * u32(parameters), data, parameters + 4)]
        prototypes.append('(' + ''.join(arguments) + ')' + types[result])
    methods = []
    for i in range(u32(88)):
        owner, proto, name = struct.unpack_from('<HHI', data, u32(92) + i * 8)
        methods.append((types[owner], strings[name], prototypes[proto]))
    fields = []
    for i in range(u32(80)):
        owner, field_type, name = struct.unpack_from('<HHI', data, u32(84) + i * 8)
        fields.append((types[owner], strings[name], types[field_type]))
    for i in range(u32(96)):
        definition = struct.unpack_from('<8I', data, u32(100) + i * 32)
        if types[definition[0]] != 'Lis/xyz/mpv/MPV;': continue
        offset = definition[6]
        assert offset, 'MPV class has no encoded members'
        sizes = []
        for _ in range(4):
            value, offset = uleb(offset); sizes.append(value)
        found_fields, found_methods = set(), set()
        for size in sizes[:2]:
            index = 0
            for _ in range(size):
                delta, offset = uleb(offset); index += delta
                _, offset = uleb(offset)
                found_fields.add(fields[index][1:])
        for size in sizes[2:]:
            index = 0
            for _ in range(size):
                delta, offset = uleb(offset); index += delta
                _, offset = uleb(offset)
                _, offset = uleb(offset)
                found_methods.add(methods[index][1:])
        return found_fields, found_methods
    return None


def verify_mpv_jni(dex_files):
    classes = [members for data in dex_files if (members := mpv_members(data)) is not None]
    assert len(classes) == 1, 'Expected one instance-based MPV class'
    fields, methods = classes[0]
    required = {
        ('eventProperty', '(Ljava/lang/String;)V'),
        ('eventProperty', '(Ljava/lang/String;J)V'),
        ('eventProperty', '(Ljava/lang/String;Z)V'),
        ('eventProperty', '(Ljava/lang/String;D)V'),
        ('eventProperty', '(Ljava/lang/String;Ljava/lang/String;)V'),
        ('eventProperty', '(Ljava/lang/String;Lis/xyz/mpv/MPVNode;)V'),
        ('event', '(ILis/xyz/mpv/MPVNode;)V'),
        ('logMessage', '(Ljava/lang/String;ILjava/lang/String;)V'),
        ('nativeCreate', '(Landroid/content/Context;)V'),
        ('nativeInit', '()V'),
        ('nativeDestroy', '()V'),
    }
    assert ('nativeHandle', 'J') in fields, 'R8 removed or changed the native context field'
    assert required <= methods, 'R8 removed JNI callbacks: ' + repr(sorted(required - methods))
