package com.civitasv.spider.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * @author wangq
 * @date 2022/2/24
 * @description
 */
@Slf4j
public class JacksonUtils {
	private static ObjectMapper objectMapper = new ObjectMapper()//
			.setSerializationInclusion(JsonInclude.Include.NON_NULL)//
			// .setSerializationInclusion(JsonInclude.Include.ALWAYS)//
			// .setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)//
			// .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)//
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) //
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS) //
			.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")) //
	//
	;
	
	
	/**
	 * javaBean,list,array convert to json string
	 */
	public static String toJson(Object obj) {
		try {
			return JacksonUtils.objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			throw new RuntimeException("obj to json failed!!!");
		}
	}
	
	/**
	 * json string convert to javaBean
	 */
	public static <T> T fromJson(String jsonStr, Class<T> clazz) {
		try {
			return JacksonUtils.objectMapper.readValue(jsonStr, clazz);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T fromJson(String jsonString, TypeReference<T> type) {
		if (StringUtils.isEmpty(jsonString)) {
			return null;
		}
		try {
			return (T) JacksonUtils.objectMapper.readValue(jsonString, type);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * json string convert to map
	 */
	@SuppressWarnings("unchecked")
	public static <T> Map<String, Object> json2Map(String jsonStr) {
		try {
			return JacksonUtils.objectMapper.readValue(jsonStr, Map.class);
		} catch (Exception e) {
			log.warn(jsonStr + " not a JSON string!! Exception:" + e.getMessage());
		}
		return null;
	}
	
	/**
	 * json string convert to map with javaBean
	 */
	public static <T> Map<String, T> json2Map(String jsonStr, Class<T> clazz) {
		Map<String, T> map = null;
		try {
			map = JacksonUtils.objectMapper.readValue(jsonStr, new TypeReference<Map<String, T>>() {
			});
		} catch (Exception e) {
			log.warn(jsonStr + " not a JSON string!! Exception:" + e.getMessage());
		}
		return map;
	}
	
	/**
	 * json array string convert to list with javaBean
	 */
	public static <T> List<T> json2List(String jsonArrayStr, Class<Map> clazz) {
		//List<T> result = new ArrayList<T>();
		try {
			List<T> ts = JacksonUtils.objectMapper.readValue(jsonArrayStr,
					new TypeReference<List<T>>() {
					});
			/*for (Map<String, Object> map : list) {
				result.add(JacksonUtils.map2pojo(map, clazz));
			}*/
			return ts;
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return  null;
		//return result;
	}
	
	/**
	 * map convert to javaBean
	 */
	public static <T> T map2pojo(Map<?, ?> map, Class<T> clazz) {
		return JacksonUtils.objectMapper.convertValue(map, clazz);
	}
	
	
	/**
	 * 反序列化复杂Collection如List<Bean>, 先使用函數createCollectionType构造类型,然后调. 如果JSON字符串为null或"null"字符串,返回null.
	 * 如果JSON字符串为"[]",返回空集合.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T fromJson(String jsonString, JavaType javaType) {
		if (StringUtils.isEmpty(jsonString)) {
			return null;
		}
		try {
			return (T) JacksonUtils.objectMapper.readValue(jsonString, javaType);
		} catch (IOException e) {
			return null;
		}
	}
	
	public static <T> T fromJson(String json, Class<T> clazz, Type type) {
		JavaType javaType = TypeFactory.defaultInstance().constructParametricType(clazz,
				TypeFactory.defaultInstance().constructType(type));
		return JacksonUtils.fromJson(json, javaType);
	}
	
}
