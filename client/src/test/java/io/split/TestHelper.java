package io.split;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestHelper {
    public static CloseableHttpClient mockHttpClient(String jsonName, int httpStatus) throws IOException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        HttpEntity entityMock = Mockito.mock(HttpEntity.class);
        Mockito.when(entityMock.getContent()).thenReturn(TestHelper.class.getClassLoader().getResourceAsStream(jsonName));

        ClassicHttpResponse httpResponseMock = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(httpResponseMock.getEntity()).thenReturn(entityMock);
        Mockito.when(httpResponseMock.getCode()).thenReturn(httpStatus);

        CloseableHttpClient httpClientMock = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(httpClientMock.execute(Mockito.anyObject())).thenReturn(classicResponseToCloseableMock(httpResponseMock));

        return httpClientMock;
    }

    private static CloseableHttpResponse classicResponseToCloseableMock(ClassicHttpResponse mocked) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method adaptMethod = CloseableHttpResponse.class.getDeclaredMethod("adapt", ClassicHttpResponse.class);
        adaptMethod.setAccessible(true);
        return (CloseableHttpResponse) adaptMethod.invoke(null, mocked);
    }
}
