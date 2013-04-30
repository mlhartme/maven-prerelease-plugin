package com.oneandone.devel.maven;

import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.ProxySelector;
import org.sonatype.aether.repository.RemoteRepository;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

public class GlobalProxySelector implements ProxySelector {
    /** return null if there's no http_proxy env entry */
    public static GlobalProxySelector forEnvOpt() throws MalformedURLException {
        String env;
        URL url;

        env = System.getenv("http_proxy");
        if (env != null) {
            url = new URL(env);
            return new GlobalProxySelector(new Proxy(null, url.getHost(), url.getPort(), null));
        } else {
            return null;
        }
    }

    private final Proxy proxy;

    public GlobalProxySelector(Proxy proxy) {
        this.proxy = proxy;
    }

    public Proxy getProxy(RemoteRepository remoteRepository) {
        URL url;
        InetAddress address;

        try {
            url = new URL(remoteRepository.getUrl());
        } catch (MalformedURLException e) {
            return null;
        }
        try {
            address = InetAddress.getByName(url.getHost());
        } catch (UnknownHostException e) {
            return null;
        }
        if (!address.isSiteLocalAddress()) {
            return proxy;
        }
        return null;
    }

    public String toString() {
        return proxy.getHost() + ":" + proxy.getPort();
    }
}
