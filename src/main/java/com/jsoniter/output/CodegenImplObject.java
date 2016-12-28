package com.jsoniter.output;

import com.jsoniter.spi.Binding;
import com.jsoniter.spi.ClassDescriptor;
import com.jsoniter.spi.Encoder;
import com.jsoniter.spi.JsoniterSpi;

class CodegenImplObject {
    public static String genObject(String cacheKey, Class clazz) {
        ClassDescriptor desc = JsoniterSpi.getClassDescriptor(clazz, false);
        StringBuilder lines = new StringBuilder();
        append(lines, String.format("public static void encode_(%s obj, com.jsoniter.output.JsonStream stream) {", clazz.getCanonicalName()));
        append(lines, "if (obj == null) { stream.writeNull(); return; }");
        if (hasFieldOutput(desc)) {
            append(lines, "stream.startObject();");
            for (Binding field : desc.allEncoderBindings()) {
                for (String toName : field.toNames) {
                    append(lines, String.format("stream.writeField(\"%s\");", toName));
                    append(lines, genField(field));
                    append(lines, "stream.writeMore();");
                }
            }
            append(lines, "stream.endObject();");
        } else {
            append(lines, "stream.writeEmptyObject();");
        }
        append(lines, "}");
        return lines.toString().replace("{{clazz}}", clazz.getCanonicalName());
    }

    private static boolean hasFieldOutput(ClassDescriptor desc) {
        for (Binding binding : desc.allEncoderBindings()) {
            if (binding.toNames.length > 0) {
                return true;
            }
        }
        return false;
    }

    private static String genField(Binding field) {
        String fieldCacheKey = field.encoderCacheKey();
        Encoder encoder = JsoniterSpi.getEncoder(fieldCacheKey);
        if (encoder == null) {
            return CodegenImplNative.genWriteOp("obj." + field.name, field.valueType);
        }
        return String.format("com.jsoniter.output.CodegenAccess.writeVal(\"%s\", obj.%s, stream);",
                fieldCacheKey, field.name);
    }

    private static void append(StringBuilder lines, String str) {
        lines.append(str);
        lines.append("\n");
    }
}
