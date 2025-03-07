package io.split.client;

import com.google.common.annotations.VisibleForTesting;
import io.split.client.dtos.SplitChange;
import io.split.client.utils.Json;
import io.split.client.utils.Utils;
import io.split.engine.experiments.SplitChangeFetcher;
import io.split.engine.metrics.Metrics;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by adilaijaz on 5/30/15.
 */
public final class HttpSplitChangeFetcher implements SplitChangeFetcher {
    private static final Logger _log = LoggerFactory.getLogger(HttpSplitChangeFetcher.class);

    private static final String SINCE = "since";
    private static final String PREFIX = "splitChangeFetcher";
    private static final String NAME_CACHE = "Cache-Control";
    private static final String VALUE_CACHE = "no-cache";

    private final CloseableHttpClient _client;
    private final URI _target;
    private final Metrics _metrics;

    public static HttpSplitChangeFetcher create(CloseableHttpClient client, URI root) throws URISyntaxException {
        return create(client, root, new Metrics.NoopMetrics());
    }

    public static HttpSplitChangeFetcher create(CloseableHttpClient client, URI root, Metrics metrics) throws URISyntaxException {
        return new HttpSplitChangeFetcher(client, Utils.appendPath(root, "api/splitChanges"), metrics);
    }

    private HttpSplitChangeFetcher(CloseableHttpClient client, URI uri, Metrics metrics) {
        _client = client;
        _target = uri;
        _metrics = metrics;
        checkNotNull(_target);
    }

    @Override
    public SplitChange fetch(long since, boolean addCacheHeader) {

        long start = System.currentTimeMillis();

        CloseableHttpResponse response = null;

        try {
            URI uri = new URIBuilder(_target).addParameter(SINCE, "" + since).build();

            HttpGet request = new HttpGet(uri);
            if(addCacheHeader) {
                request.setHeader(NAME_CACHE, VALUE_CACHE);
            }
            response = _client.execute(request);

            int statusCode = response.getCode();

            if (statusCode < 200 || statusCode >= 300) {
                _metrics.count(PREFIX + ".status." + statusCode, 1);
                throw new IllegalStateException("Could not retrieve splitChanges; http return code " + statusCode);
            }


            String json = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (_log.isDebugEnabled()) {
                _log.debug("Received json: " + json);
            }

            return Json.fromJson(json, SplitChange.class);
        } catch (Throwable t) {
            _metrics.count(PREFIX + ".exception", 1);
            throw new IllegalStateException("Problem fetching splitChanges: " + t.getMessage(), t);
        } finally {
            Utils.forceClose(response);
            _metrics.time(PREFIX + ".time", System.currentTimeMillis() - start);
        }
    }

    @VisibleForTesting
    URI getTarget() {
        return _target;
    }
}
