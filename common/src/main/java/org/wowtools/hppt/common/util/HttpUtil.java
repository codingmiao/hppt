package org.wowtools.hppt.common.util;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * @author liuyu
 * @date 2023/12/22
 */
@Slf4j
public class HttpUtil {

    private static final okhttp3.MediaType bytesMediaType = okhttp3.MediaType.parse("application/octet-stream");
    private static final byte[] emptyBts = new byte[0];

    private static final OkHttpClient okHttpClient;

    static {
        okHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory(), x509TrustManager())
                // 是否开启缓存
                .retryOnConnectionFailure(false)
                .connectionPool(pool())
                .connectTimeout(30L, TimeUnit.SECONDS)
                .readTimeout(30L, TimeUnit.SECONDS)
                .writeTimeout(30L, TimeUnit.SECONDS)
                .hostnameVerifier((hostname, session) -> true)
                // 设置代理
//                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 8888)))
                // 拦截器
//                .addInterceptor()
                .build();

    }


    public static Response doGet(String url) {
        Request.Builder builder = new Request.Builder();
        Request request = builder.url(url).build();
        return execute(request);
    }

    public static Response doPost(String url) {
        RequestBody body = RequestBody.create(bytesMediaType, emptyBts);
        Request request = new Request.Builder().url(url).post(body).build();
        return execute(request);
    }

    public static Response doPost(String url, byte[] bytes) {
        RequestBody body = RequestBody.create(bytesMediaType, bytes == null ? emptyBts : bytes);
        Request request = new Request.Builder().url(url).post(body).build();
        return execute(request);
    }


    public static Response execute(Request request) {
        java.io.InterruptedIOException interruptedIOException = null;
        //做一个循环防止被假唤醒打断
        for (int i = 0; i < 5; i++) {
            try {
                return okHttpClient.newCall(request).execute();
            } catch (java.io.InterruptedIOException e) {
                interruptedIOException = e;
                try {
                    Thread.sleep(10);
                } catch (Exception ex) {
                    log.debug("发送请求sleep被打断");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException(interruptedIOException);

    }


    private static X509TrustManager x509TrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private static SSLSocketFactory sslSocketFactory() {
        try {
            // 信任任何链接
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{x509TrustManager()}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private static ConnectionPool pool() {
        return new ConnectionPool(5, 1L, TimeUnit.MINUTES);
    }

}
