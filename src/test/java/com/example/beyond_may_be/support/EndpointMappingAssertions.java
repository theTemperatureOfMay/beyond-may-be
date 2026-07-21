package com.example.beyond_may_be.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

public final class EndpointMappingAssertions {
  private EndpointMappingAssertions() {}

  public static void assertEndpoint(
      String controllerClassName,
      String methodName,
      Class<? extends Annotation> mappingType,
      String expectedPath) {
    try {
      Class<?> controllerType = Class.forName(controllerClassName);
      assertTrue(controllerType.isAnnotationPresent(RestController.class));
      RequestMapping root = controllerType.getAnnotation(RequestMapping.class);
      assertNotNull(root);
      Method method =
          Arrays.stream(controllerType.getDeclaredMethods())
              .filter(candidate -> candidate.getName().equals(methodName))
              .findFirst()
              .orElseThrow();
      Annotation mapping = method.getAnnotation(mappingType);
      assertNotNull(mapping);
      String[] methodPaths = (String[]) mappingType.getMethod("value").invoke(mapping);
      String methodPath = methodPaths.length == 0 ? "" : methodPaths[0];
      assertEquals(expectedPath, root.value()[0] + methodPath);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }
}
