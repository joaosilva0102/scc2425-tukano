package utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

final public class JSON {
	final static ObjectMapper mapper = new ObjectMapper();

	synchronized public static String encode(Object obj) {
		try {
			return mapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return "";
		}
	}

	synchronized public static <T> T decode(String json, Class<T> classOf) {
		try {
			return mapper.readValue(json, classOf);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}

	synchronized public static <T> T decode(String json, TypeReference<T> typeOf) {
		try {
			return mapper.readValue(json, typeOf);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}
}