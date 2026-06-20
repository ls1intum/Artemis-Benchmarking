package de.tum.cit.aet.service.artemis.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tum.cit.aet.artemisModel.Course;
import de.tum.cit.aet.artemisModel.Exam;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.codec.multipart.Part;
import org.springframework.test.web.reactive.server.HttpHandlerConnector;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

class SimulatedArtemisAdminTest {

    private static final String AUTH_COOKIE = "jwt=token; Path=/; Max-Age=3600; Expires=Tue, 01 Jan 2030 00:00:00 GMT";

    @Test
    void createCourse_sendsCourseCreateDtoWithDefaults() {
        Queue<CapturedRequest> captured = new ConcurrentLinkedQueue<>();
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .POST("/api/core/public/authenticate", request -> captureWithoutBody(request, captured, loginResponse()))
            .GET("/api/core/public/account", request -> captureWithoutBody(request, captured, accountResponse()))
            .POST("/api/admin/courses", request ->
                captureMultipart(request, captured, "course", HttpStatus.CREATED, "{\"id\":1,\"title\":\"Temporary Benchmarking Course\"}")
            )
            .build();

        SimulatedArtemisAdmin admin = new SimulatedArtemisAdmin("http://localhost", "admin", "admin", webClientSupplier(router));
        admin.login();
        Course created = admin.createCourse();

        assertNotNull(created);

        CapturedRequest createCourseRequest = findRequest(captured, "/api/admin/courses");
        assertNotNull(createCourseRequest);

        String body = createCourseRequest.body();
        assertTrue(body.contains("\"accuracyOfScores\":1"));
        assertTrue(body.contains("\"courseInformationSharingConfiguration\":\"COMMUNICATION_AND_MESSAGING\""));
        assertTrue(body.contains("\"maxComplaints\":0"));
        assertTrue(body.contains("\"maxComplaintTimeDays\":0"));
        assertTrue(body.contains("\"enrollmentEnabled\":false"));
        assertTrue(body.contains("\"onlineCourse\":false"));
    }

    @Test
    void createExam_sendsRequiredFields() {
        Queue<CapturedRequest> captured = new ConcurrentLinkedQueue<>();
        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .POST("/api/core/public/authenticate", request -> captureWithoutBody(request, captured, loginResponse()))
            .GET("/api/core/public/account", request -> captureWithoutBody(request, captured, accountResponse()))
            .POST("/api/exam/courses/1/exams", request ->
                captureJson(request, captured, HttpStatus.CREATED, "{\"id\":10,\"title\":\"Temporary Exam\"}")
            )
            .build();

        SimulatedArtemisAdmin admin = new SimulatedArtemisAdmin("http://localhost", "admin", "admin", webClientSupplier(router));
        admin.login();

        Course course = new Course();
        course.setId(1L);
        admin.createExam(course);

        CapturedRequest createExamRequest = findRequest(captured, "/api/exam/courses/1/exams");
        assertNotNull(createExamRequest);

        String body = createExamRequest.body();
        assertTrue(body.contains("\"numberOfCorrectionRoundsInExam\":1"));
        assertTrue(body.contains("\"numberOfExercisesInExam\":4"));
        assertTrue(body.contains("\"examMaxPoints\":4"));
        assertTrue(body.contains("\"course\":{\"id\":1"));
        assertTrue(body.contains("\"visibleDate\""));
        assertTrue(body.contains("\"startDate\""));
        assertTrue(body.contains("\"endDate\""));
    }

    @Test
    void createExamExercises_handlesNullMandatoryInExerciseGroupResponse() {
        Queue<CapturedRequest> captured = new ConcurrentLinkedQueue<>();
        AtomicInteger exerciseGroupCount = new AtomicInteger();

        RouterFunction<ServerResponse> router = RouterFunctions.route()
            .POST("/api/core/public/authenticate", request -> captureWithoutBody(request, captured, loginResponse()))
            .GET("/api/core/public/account", request -> captureWithoutBody(request, captured, accountResponse()))
            .POST("/api/exam/courses/1/exams/2/exercise-groups", request -> {
                int index = exerciseGroupCount.incrementAndGet();
                String responseBody =
                    index == 1
                        ? "{\"id\":1,\"title\":\"Text Exercise Group\",\"mandatory\":true,\"exam\":{\"id\":2,\"course\":{\"id\":1},\"exerciseGroups\":[{\"id\":10,\"title\":\"Nested\",\"mandatory\":null}]}}"
                        : "{\"id\":" + index + ",\"title\":\"Exercise Group " + index + "\",\"mandatory\":true}";
                return captureJson(request, captured, HttpStatus.CREATED, responseBody);
            })
            .POST("/api/text/text-exercises", request ->
                captureWithoutBody(request, captured, ServerResponse.status(HttpStatus.CREATED).build())
            )
            .POST("/api/modeling/modeling-exercises", request ->
                captureWithoutBody(request, captured, ServerResponse.status(HttpStatus.CREATED).build())
            )
            .POST("/api/programming/programming-exercises/setup", request ->
                captureWithoutBody(request, captured, ServerResponse.status(HttpStatus.CREATED).build())
            )
            .POST("/api/quiz/exercise-groups/{exerciseGroupId}/quiz-exercises", request ->
                captureWithoutBody(request, captured, ServerResponse.status(HttpStatus.CREATED).build())
            )
            .POST("/api/fileupload/file-upload-exercises", request ->
                captureWithoutBody(request, captured, ServerResponse.status(HttpStatus.CREATED).build())
            )
            .build();

        SimulatedArtemisAdmin admin = new SimulatedArtemisAdmin("http://localhost", "admin", "admin", webClientSupplier(router));
        admin.login();

        Course course = new Course();
        course.setId(1L);
        Exam exam = new Exam();
        exam.setId(2L);
        exam.setCourse(course);

        admin.createExamExercises(course.getId(), exam);

        assertEquals(12, captured.size());
    }

    private static Supplier<WebClient.Builder> webClientSupplier(RouterFunction<ServerResponse> router) {
        ClientHttpConnector connector = new HttpHandlerConnector(RouterFunctions.toHttpHandler(router));
        return () -> WebClient.builder().clientConnector(connector);
    }

    private static Mono<ServerResponse> loginResponse() {
        return ServerResponse.ok().header(HttpHeaders.SET_COOKIE, AUTH_COOKIE).build();
    }

    private static Mono<ServerResponse> accountResponse() {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue("{\"authorities\":[\"ROLE_SUPER_ADMIN\"]}");
    }

    private static Mono<ServerResponse> captureWithoutBody(
        ServerRequest request,
        Queue<CapturedRequest> captured,
        Mono<ServerResponse> response
    ) {
        captured.add(new CapturedRequest(request.method().name(), request.path(), request.headers().asHttpHeaders(), ""));
        return response;
    }

    private static Mono<ServerResponse> captureJson(
        ServerRequest request,
        Queue<CapturedRequest> captured,
        HttpStatus status,
        String responseBody
    ) {
        return request
            .bodyToMono(String.class)
            .defaultIfEmpty("")
            .doOnNext(body ->
                captured.add(new CapturedRequest(request.method().name(), request.path(), request.headers().asHttpHeaders(), body))
            )
            .then(ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).bodyValue(responseBody));
    }

    private static Mono<ServerResponse> captureMultipart(
        ServerRequest request,
        Queue<CapturedRequest> captured,
        String partName,
        HttpStatus status,
        String responseBody
    ) {
        return extractMultipartPart(request, partName)
            .defaultIfEmpty("")
            .doOnNext(body ->
                captured.add(new CapturedRequest(request.method().name(), request.path(), request.headers().asHttpHeaders(), body))
            )
            .then(ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).bodyValue(responseBody));
    }

    private static Mono<String> extractMultipartPart(ServerRequest request, String partName) {
        return request.body(BodyExtractors.toMultipartData()).flatMap(parts -> {
            Part part = parts.getFirst(partName);
            if (part == null) {
                return Mono.just("");
            }
            return DataBufferUtils.join(part.content()).map(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                return new String(bytes, StandardCharsets.UTF_8);
            });
        });
    }

    private static CapturedRequest findRequest(Queue<CapturedRequest> captured, String path) {
        return captured
            .stream()
            .filter(req -> req.path().equals(path))
            .findFirst()
            .orElse(null);
    }

    private record CapturedRequest(String method, String path, HttpHeaders headers, String body) {}
}
