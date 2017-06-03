package com.jsoniter.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.jsoniter.annotation.GsonAnnotationSupport;
import junit.framework.TestCase;

public class TestGson extends TestCase {

    public void setUp() {
        GsonAnnotationSupport.enable();
    }

    public void tearDown() {
        GsonAnnotationSupport.disable();
    }

    public static class TestObject1 {
        @SerializedName("field-1")
        public String field1;
    }

    public void test_SerializedName_on_field() {
        Gson gson = new GsonBuilder().create();
        TestObject1 obj = new TestObject1();
        obj.field1 = "hello";
        String output = gson.toJson(obj);
        assertEquals("{\"field-1\":\"hello\"}", output);
        output = JsonStream.serialize(obj);
        assertEquals("{\"field-1\":\"hello\"}", output);
    }

    public static class TestObject2 {
        @Expose(serialize = false)
        public String field1;
    }

    public void test_Expose() {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        TestObject2 obj = new TestObject2();
        obj.field1 = "hello";
        String output = gson.toJson(obj);
        assertEquals("{}", output);
        output = JsonStream.serialize(obj);
        assertEquals("{}", output);
    }
}
