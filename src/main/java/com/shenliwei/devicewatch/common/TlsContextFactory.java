package com.shenliwei.devicewatch.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Builds JSSE contexts from PKCS12 keystore / truststore files.
 */
public final class TlsContextFactory {

    private TlsContextFactory() {
    }

    public static SSLContext clientContext(Path truststore, String password)
            throws IOException, GeneralSecurityException {
        KeyStore store = loadStore(truststore, password);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), null);
        return context;
    }

    public static SSLServerSocketFactory serverFactory(Path keystore, String password)
            throws IOException, GeneralSecurityException {
        KeyStore store = loadStore(keystore, password);
        KeyManagerFactory factory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store, password.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(factory.getKeyManagers(), null, null);
        return context.getServerSocketFactory();
    }

    public static String firstCertSubject(Path keystore, String password)
            throws IOException, GeneralSecurityException {
        KeyStore store = loadStore(keystore, password);
        String alias = store.aliases().nextElement();
        X509Certificate certificate = (X509Certificate) store.getCertificate(alias);
        return certificate.getSubjectX500Principal().getName();
    }

    private static KeyStore loadStore(Path path, String password)
            throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(path)) {
            store.load(input, password.toCharArray());
        }
        return store;
    }
}
