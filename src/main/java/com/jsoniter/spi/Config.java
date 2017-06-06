package com.jsoniter.spi;

import com.jsoniter.annotation.*;
import com.jsoniter.output.EncodingMode;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

public class Config extends EmptyExtension {

    private final String configName;
    private final Builder builder;
    private static volatile Map<String, Config> configs = new HashMap<String, Config>();
    private volatile Map<Type, String> decoderCacheKeys = new HashMap<Type, String>();
    private volatile Map<Type, String> encoderCacheKeys = new HashMap<Type, String>();

    protected Config(String configName, Builder builder) {
        this.configName = configName;
        this.builder = builder;
    }

    public String configName() {
        return configName;
    }

    public String getDecoderCacheKey(Type type) {
        String cacheKey = decoderCacheKeys.get(type);
        if (cacheKey != null) {
            return cacheKey;
        }
        synchronized (this) {
            cacheKey = decoderCacheKeys.get(type);
            if (cacheKey != null) {
                return cacheKey;
            }
            cacheKey = TypeLiteral.create(type).getDecoderCacheKey(configName);
            HashMap<Type, String> newCache = new HashMap<Type, String>(decoderCacheKeys);
            newCache.put(type, cacheKey);
            decoderCacheKeys = newCache;
            return cacheKey;
        }
    }

    public String getEncoderCacheKey(Type type) {
        String cacheKey = encoderCacheKeys.get(type);
        if (cacheKey != null) {
            return cacheKey;
        }
        synchronized (this) {
            cacheKey = encoderCacheKeys.get(type);
            if (cacheKey != null) {
                return cacheKey;
            }
            cacheKey = TypeLiteral.create(type).getEncoderCacheKey(configName);
            HashMap<Type, String> newCache = new HashMap<Type, String>(encoderCacheKeys);
            newCache.put(type, cacheKey);
            encoderCacheKeys = newCache;
            return cacheKey;
        }
    }

    public DecodingMode decodingMode() {
        return builder.decodingMode;
    }

    protected Builder builder() {
        return builder;
    }

    public Builder copyBuilder() {
        return builder.copy();
    }

    public int indentionStep() {
        return builder.indentionStep;
    }

    public boolean escapeUnicode() {
        return builder.escapeUnicode;
    }

    public EncodingMode encodingMode() {
        return builder.encodingMode;
    }

    public static class Builder {

        private DecodingMode decodingMode;
        private EncodingMode encodingMode;
        private int indentionStep;
        private boolean escapeUnicode = true;

        public Builder() {
            String envMode = System.getenv("JSONITER_DECODING_MODE");
            if (envMode != null) {
                decodingMode = DecodingMode.valueOf(envMode);
            } else {
                decodingMode = DecodingMode.REFLECTION_MODE;
            }
            envMode = System.getenv("JSONITER_ENCODING_MODE");
            if (envMode != null) {
                encodingMode = EncodingMode.valueOf(envMode);
            } else {
                encodingMode = EncodingMode.REFLECTION_MODE;
            }
        }

        public Builder decodingMode(DecodingMode decodingMode) {
            this.decodingMode = decodingMode;
            return this;
        }

        public Builder encodingMode(EncodingMode encodingMode) {
            this.encodingMode = encodingMode;
            return this;
        }

        public Builder indentionStep(int indentionStep) {
            this.indentionStep = indentionStep;
            return this;
        }

        public Builder escapeUnicode(boolean escapeUnicode) {
            this.escapeUnicode = escapeUnicode;
            return this;
        }

        public Config build() {
            String configName = JsoniterSpi.assignConfigName(this);
            Config config = configs.get(configName);
            if (config != null) {
                return config;
            }
            synchronized (Config.class) {
                config = configs.get(configName);
                if (config != null) {
                    return config;
                }
                config = doBuild(configName);
                HashMap<String, Config> newCache = new HashMap<String, Config>(configs);
                newCache.put(configName, config);
                configs = newCache;
                return config;
            }
        }

        protected Config doBuild(String configName) {
            return new Config(configName, this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Builder builder = (Builder) o;

            if (indentionStep != builder.indentionStep) return false;
            if (escapeUnicode != builder.escapeUnicode) return false;
            if (decodingMode != builder.decodingMode) return false;
            return encodingMode == builder.encodingMode;
        }

        @Override
        public int hashCode() {
            int result = decodingMode != null ? decodingMode.hashCode() : 0;
            result = 31 * result + (encodingMode != null ? encodingMode.hashCode() : 0);
            result = 31 * result + indentionStep;
            result = 31 * result + (escapeUnicode ? 1 : 0);
            return result;
        }

        public Builder copy() {
            Builder builder = new Builder();
            builder.encodingMode = encodingMode;
            builder.decodingMode = decodingMode;
            builder.indentionStep = indentionStep;
            builder.escapeUnicode = escapeUnicode;
            return builder;
        }
    }

    public static final Config INSTANCE = new Builder().build();

    @Override
    public void updateClassDescriptor(ClassDescriptor desc) {
        JsonObject jsonObject = (JsonObject) desc.clazz.getAnnotation(JsonObject.class);
        if (jsonObject != null) {
            if (jsonObject.asExtraForUnknownProperties()) {
                desc.asExtraForUnknownProperties = true;
            }
            for (String fieldName : jsonObject.unknownPropertiesWhitelist()) {
                Binding binding = new Binding(desc.classInfo, desc.lookup, Object.class);
                binding.name = fieldName;
                binding.fromNames = new String[]{binding.name};
                binding.toNames = new String[0];
                binding.shouldSkip = true;
                desc.fields.add(binding);
            }
            for (String fieldName : jsonObject.unknownPropertiesBlacklist()) {
                Binding binding = new Binding(desc.classInfo, desc.lookup, Object.class);
                binding.name = fieldName;
                binding.fromNames = new String[]{binding.name};
                binding.toNames = new String[0];
                binding.asExtraWhenPresent = true;
                desc.fields.add(binding);
            }
        }
        List<Method> allMethods = new ArrayList<Method>();
        Class current = desc.clazz;
        while (current != null) {
            allMethods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        updateBindings(desc);
        detectCtor(desc);
        detectStaticFactory(desc, allMethods);
        detectWrappers(desc, allMethods);
        detectUnwrappers(desc, allMethods);
    }

    private void detectUnwrappers(ClassDescriptor desc, List<Method> allMethods) {
        for (Method method : allMethods) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (getJsonUnwrapper(method.getAnnotations()) == null) {
                continue;
            }
            desc.unwrappers.add(new UnwrapperDescriptor(method));
        }
    }

    private void detectWrappers(ClassDescriptor desc, List<Method> allMethods) {
        for (Method method : allMethods) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            JsonWrapper jsonWrapper = getJsonWrapper(method.getAnnotations());
            if (jsonWrapper == null) {
                continue;
            }
            Annotation[][] annotations = method.getParameterAnnotations();
            String[] paramNames = getParamNames(method, annotations.length);
            if (JsonWrapperType.BINDING.equals(jsonWrapper.value())) {
                WrapperDescriptor wrapper = new WrapperDescriptor();
                wrapper.method = method;
                for (int i = 0; i < annotations.length; i++) {
                    Annotation[] paramAnnotations = annotations[i];
                    Binding binding = new Binding(desc.classInfo, desc.lookup, method.getGenericParameterTypes()[i]);
                    JsonProperty jsonProperty = getJsonProperty(paramAnnotations);
                    if (jsonProperty != null) {
                        updateBindingWithJsonProperty(binding, jsonProperty);
                    }
                    if (binding.name == null || binding.name.length() == 0) {
                        binding.name = paramNames[i];
                    }
                    binding.fromNames = new String[]{binding.name};
                    binding.toNames = new String[]{binding.name};
                    binding.annotations = paramAnnotations;
                    wrapper.parameters.add(binding);
                }
                desc.bindingTypeWrappers.add(wrapper);
            } else if (JsonWrapperType.KEY_VALUE.equals(jsonWrapper.value())) {
                desc.keyValueTypeWrappers.add(method);
            } else {
                throw new JsonException("unknown json wrapper type: " + jsonWrapper.value());
            }
        }
    }

    private String[] getParamNames(Object obj, int paramCount) {
        String[] paramNames = new String[paramCount];
        try {
            Object params = reflectCall(obj, "getParameters");
            for (int i = 0; i < paramNames.length; i++) {
                paramNames[i] = (String) reflectCall(Array.get(params, i), "getName");
            }
        } catch (Exception e) {
        }
        return paramNames;
    }

    private Object reflectCall(Object obj, String methodName, Object... args) throws Exception {
        Method method = obj.getClass().getMethod(methodName);
        return method.invoke(obj, args);
    }

    private void detectStaticFactory(ClassDescriptor desc, List<Method> allMethods) {
        for (Method method : allMethods) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            JsonCreator jsonCreator = getJsonCreator(method.getAnnotations());
            if (jsonCreator == null) {
                continue;
            }
            desc.ctor.staticMethodName = method.getName();
            desc.ctor.staticFactory = method;
            desc.ctor.ctor = null;
            Annotation[][] annotations = method.getParameterAnnotations();
            String[] paramNames = getParamNames(method, annotations.length);
            for (int i = 0; i < annotations.length; i++) {
                Annotation[] paramAnnotations = annotations[i];
                JsonProperty jsonProperty = getJsonProperty(paramAnnotations);
                Binding binding = new Binding(desc.classInfo, desc.lookup, method.getGenericParameterTypes()[i]);
                if (jsonProperty != null) {
                    updateBindingWithJsonProperty(binding, jsonProperty);
                }
                if (binding.name == null || binding.name.length() == 0) {
                    binding.name = paramNames[i];
                }
                binding.fromNames = new String[]{binding.name};
                binding.toNames = new String[]{binding.name};
                binding.annotations = paramAnnotations;
                desc.ctor.parameters.add(binding);
            }
        }
    }

    private void detectCtor(ClassDescriptor desc) {
        for (Constructor ctor : desc.clazz.getDeclaredConstructors()) {
            JsonCreator jsonCreator = getJsonCreator(ctor.getAnnotations());
            if (jsonCreator == null) {
                continue;
            }
            desc.ctor.staticMethodName = null;
            desc.ctor.ctor = ctor;
            desc.ctor.staticFactory = null;
            Annotation[][] annotations = ctor.getParameterAnnotations();
            String[] paramNames = getParamNames(ctor, annotations.length);
            for (int i = 0; i < annotations.length; i++) {
                Annotation[] paramAnnotations = annotations[i];
                JsonProperty jsonProperty = getJsonProperty(paramAnnotations);
                Binding binding = new Binding(desc.classInfo, desc.lookup, ctor.getGenericParameterTypes()[i]);
                if (jsonProperty != null) {
                    updateBindingWithJsonProperty(binding, jsonProperty);
                }
                if (binding.name == null || binding.name.length() == 0) {
                    binding.name = paramNames[i];
                }
                binding.fromNames = new String[]{binding.name};
                binding.toNames = new String[]{binding.name};
                binding.annotations = paramAnnotations;
                desc.ctor.parameters.add(binding);
            }
        }
    }

    private void updateBindings(ClassDescriptor desc) {
        for (Binding binding : desc.allBindings()) {
            JsonIgnore jsonIgnore = getJsonIgnore(binding.annotations);
            if (jsonIgnore != null) {
                if (jsonIgnore.ignoreDecoding()) {
                    binding.fromNames = new String[0];
                }
                if (jsonIgnore.ignoreEncoding()) {
                    binding.toNames = new String[0];
                }
            }
            // map JsonUnwrapper is not getter
            JsonUnwrapper jsonUnwrapper = getJsonUnwrapper(binding.annotations);
            if (jsonUnwrapper != null) {
                binding.fromNames = new String[0];
                binding.toNames = new String[0];
            }
            JsonProperty jsonProperty = getJsonProperty(binding.annotations);
            if (jsonProperty != null) {
                updateBindingWithJsonProperty(binding, jsonProperty);
            }
            if (getAnnotation(binding.annotations, JsonMissingProperties.class) != null) {
                // this binding will not bind from json
                // instead it will be set by jsoniter with missing property names
                binding.fromNames = new String[0];
                desc.onMissingProperties = binding;
            }
            if (getAnnotation(binding.annotations, JsonExtraProperties.class) != null) {
                // this binding will not bind from json
                // instead it will be set by jsoniter with extra properties
                binding.fromNames = new String[0];
                desc.onExtraProperties = binding;
            }
        }
    }

    private void updateBindingWithJsonProperty(Binding binding, JsonProperty jsonProperty) {
        binding.asMissingWhenNotPresent = jsonProperty.required();
        binding.isNullable = jsonProperty.nullable();
        binding.isCollectionValueNullable = jsonProperty.collectionValueNullable();
        binding.shouldOmitNull = jsonProperty.omitNull();
        String altName = jsonProperty.value();
        if (!altName.isEmpty()) {
            binding.name = altName;
        }
        if (jsonProperty.from().length > 0) {
            binding.fromNames = jsonProperty.from();
        }
        if (jsonProperty.to().length > 0) {
            binding.toNames = jsonProperty.to();
        }
        if (jsonProperty.decoder() != Decoder.class) {
            try {
                binding.decoder = jsonProperty.decoder().newInstance();
            } catch (Exception e) {
                throw new JsonException(e);
            }
        }
        if (jsonProperty.encoder() != Encoder.class) {
            try {
                binding.encoder = jsonProperty.encoder().newInstance();
            } catch (Exception e) {
                throw new JsonException(e);
            }
        }
        if (jsonProperty.implementation() != Object.class) {
            binding.valueType = GenericsHelper.useImpl(binding.valueType, jsonProperty.implementation());
            binding.valueTypeLiteral = TypeLiteral.create(binding.valueType);
        }
    }

    protected JsonWrapper getJsonWrapper(Annotation[] annotations) {
        return getAnnotation(annotations, JsonWrapper.class);
    }

    protected JsonUnwrapper getJsonUnwrapper(Annotation[] annotations) {
        return getAnnotation(annotations, JsonUnwrapper.class);
    }

    protected JsonCreator getJsonCreator(Annotation[] annotations) {
        return getAnnotation(annotations, JsonCreator.class);
    }

    protected JsonProperty getJsonProperty(Annotation[] annotations) {
        return getAnnotation(annotations, JsonProperty.class);
    }

    protected JsonIgnore getJsonIgnore(Annotation[] annotations) {
        return getAnnotation(annotations, JsonIgnore.class);
    }

    protected static <T extends Annotation> T getAnnotation(Annotation[] annotations, Class<T> annotationClass) {
        if (annotations == null) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotationClass.isAssignableFrom(annotation.getClass())) {
                return (T) annotation;
            }
        }
        return null;
    }
}
