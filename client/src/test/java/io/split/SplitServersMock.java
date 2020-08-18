package io.split;

import com.google.common.collect.ImmutableMap;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.utils.Pair;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.server.ResourceConfig;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SplitServersMock {
    private static final String BASE_URL = "http://localhost:%d";
    private final SseEventQueue _queue;
    private final Validator _validator;
    private final AtomicInteger _port = new AtomicInteger();
    private HttpServer _server;
    private final Phaser _waiter = new Phaser();

    public static final OutboundEvent STOP_SIGNAL_EVENT = new OutboundEvent.Builder().comment("COMMENT").build();

    public SplitServersMock(SseEventQueue queue, Validator validator) {
        _queue = queue;
        _validator = validator;
    }

    public synchronized void start() throws IOException {
        if (null != _server) {
            throw new IllegalStateException("server is already running");
        }

        _port.set(getFreePort());
        _server = GrizzlyHttpServerFactory.createHttpServer(URI.create(String.format(BASE_URL, _port.get())),
                new ResourceConfig()
                        .register(SseResource.class)
                        .register(new AbstractBinder() {
                            @Override
                            protected void configure() {
                                bind(_queue).to(SseEventQueue.class);
                                bind(_validator).to(Validator.class);
                                bind(_waiter).to(Phaser.class);
                            }
                        }));
    }

    public synchronized void stop() {
        if (null == _server) {
            throw new IllegalStateException("Server is not running");
        }
        _queue.push(STOP_SIGNAL_EVENT);
        _server.shutdownNow();
        _waiter.arriveAndAwaitAdvance();
    }

    public int getPort() { return _port.get(); }

    private int getFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    @Singleton
    @Path("")
    public static class SseResource {
        private final SseEventQueue _eventsToSend;
        private final Validator _validator;
        private final Phaser _waiter;

        @Inject
        public SseResource(SseEventQueue queue, Validator validator, Phaser waiter) {
            _eventsToSend = queue;
            _validator = validator;
            _waiter = waiter;
        }

        @GET
        @Path("/ping")
        public Response ping() {
            return Response.ok().entity("Service online").build();
        }

        @GET
        @Produces("text/event-stream")
        public void getServerSentEvents(@Context SseEventSink eventSink,
                                        @Context Sse sse,
                                        @QueryParam("channels") String channels,
                                        @QueryParam("v") String version,
                                        @QueryParam("accessToken") String token) {
            _waiter.register();
            Thread current = new Thread(() -> {
                try {
                    Pair<OutboundSseEvent, Boolean> validationResult = _validator.validate(token, version, channels);
                    if (validationResult.getFirst() != null) { // if we need to send an event
                        eventSink.send(validationResult.getFirst());
                    }

                    if (!validationResult.getSecond()) { // if validation failed and request should be aborted
                        eventSink.close();
                        return;
                    }

                    while (!eventSink.isClosed()) {
                        try {
                            OutboundSseEvent event = _eventsToSend.pull();
                            if (STOP_SIGNAL_EVENT == event) {
                                eventSink.close();
                                break;
                            }
                            eventSink.send(event);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                } finally {
                    _waiter.arriveAndDeregister();
                }
            });
            current.start();
        }

        @GET
        @Path("/api/auth/{status}")
        public Response auth(@Context Request request, @PathParam("status") String status) {
            switch (status) {
                case "enabled":
                    return Response
                            .ok(readFileAsString("streaming-auth-push-enabled.json"))
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build();
                case "disabled":
                    return Response
                            .ok(readFileAsString("streaming-auth-push-disabled.json"))
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build();
                default:
                    return Response.status(404).build();
            }
        }

        private static final ImmutableMap<Long, String> splitChangesFiles = new ImmutableMap.Builder<Long, String>()
                .put(-1L, readFileAsString("splits.json"))
                .put(1585948850109L, "{\"splits\": [], \"since\":1585948850109, \"till\":1585948850110}")
                .put(1585948850110L, readFileAsString("splits2.json"))
                .put(1585948850111L, readFileAsString("splits_killed.json"))
                .build();


        @GET
        @Path("/api/splitChanges")
        public Response splitChanges(@Context Request request, @QueryParam("since") Long since) {
            String body = splitChangesFiles.get(since);
            if (null == body) {
                return Response.status(400).build();
            }
            return Response.ok(body).type(MediaType.APPLICATION_JSON_TYPE).build();
        }

        @GET
        @Path("/api/segmentChanges/{segmentName}")
        public Response segmentChanges(@Context Request request, @PathParam("segmentName") String segmentName, @QueryParam("since") Long since) {
            switch (segmentName) {
                case "segment-test":
                    return Response
                            .ok("{\"name\": \"segment-test\",\"added\": [],\"removed\": [],\"since\": -1,\"till\": -1}")
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build();
                case "segment3":
                    if (since == -1L) {
                        return Response.ok(readFileAsString("segment3.json")).type(MediaType.APPLICATION_JSON_TYPE).build();
                    }
                    return Response
                            .ok("{\"name\": \"segment3\",\"added\": [],\"removed\": [],\"since\": 1585948850110,\"till\": 1585948850110}")
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build();
            }
            return Response.status(404).build();
        }

        @POST
        @Path("/api/metrics/time")
        public Response timeMetric() { return Response.ok().build(); }

        @POST
        @Path("/api/metrics/times")
        public Response timesMetric() { return Response.ok().build(); }

        @POST
        @Path("/api/metrics/counter")
        public Response counterMetric() { return Response.ok().build(); }

        @POST
        @Path("/api/metrics/counters")
        public Response countersMetric() { return Response.ok().build(); }

        @POST
        @Path("/api/testImpressions/bulk")
        public Response impressions() { return Response.ok().build(); }

        @POST
        @Path("/api/events/bulk")
        public Response events() { return Response.ok().build(); }

        private static String readFileAsString(String fileName) {
            InputStream inputStream = SplitServersMock.class.getClassLoader().getResourceAsStream(fileName);
            assert inputStream != null;
            Scanner sc = new Scanner(inputStream);
            StringBuilder sb = new StringBuilder();
            while(sc.hasNext()){
                sb.append(sc.nextLine());
            }
            return sb.toString();
        }
    }


    @Singleton
    @Service
    public static class SseEventQueue {
        private final LinkedBlockingQueue<OutboundSseEvent> _queuedEvents;

        public SseEventQueue() { _queuedEvents = new LinkedBlockingQueue<>(); }
        public void push(OutboundSseEvent e) { _queuedEvents.offer(e); }
        OutboundSseEvent pull() throws InterruptedException { return _queuedEvents.take(); }
        OutboundSseEvent poll(Long l, TimeUnit tu) throws InterruptedException { return _queuedEvents.poll(l, tu); }
    }


    public interface Validator {
        Pair<OutboundSseEvent, Boolean> validate(String token, String version, String channel);
    }
}
