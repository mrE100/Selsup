import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Semaphore;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final Semaphore semaphore;
    private final ReentrantLock lock;
    private final HttpClient httpClient;
    private long lastRequestTime;
    private int requestCount;

    /**
     * @param timeUnit     the time unit for rate limiting (seconds, minutes, etc.)
     * @param requestLimit maximum number of requests allowed in the specified time unit
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }

        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit);
        this.lock = new ReentrantLock();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.lastRequestTime = System.currentTimeMillis();
        this.requestCount = 0;
    }

    /**
     * @param document the document to create
     * @param signature the signature for the document
     * @throws InterruptedException if the thread is interrupted while waiting for the rate limit
     * @throws IOException if an I/O error occurs when sending the request
     * @throws RuntimeException if the API request fails
     */
    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        try {
            // Acquire a permit with rate limiting
            acquirePermit();

            // Convert document to JSON
            String requestBody = JsonUtils.toJson(document);

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Send request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Check response status
            if (response.statusCode() >= 400) {
                throw new RuntimeException("API request failed with status code: " + response.statusCode());
            }
        } finally {
            // Release the permit after request is complete
            semaphore.release();
        }
    }

    private void acquirePermit() throws InterruptedException {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            long timeElapsed = currentTime - lastRequestTime;

            // If time window has passed, reset the counter
            if (timeElapsed >= timeUnit.toMillis(1)) {
                requestCount = 0;
                lastRequestTime = currentTime;
            }

            // If we've reached the limit, wait until the time window resets
            if (requestCount >= requestLimit) {
                long timeToWait = timeUnit.toMillis(1) - timeElapsed;
                Thread.sleep(timeToWait);
                requestCount = 0;
                lastRequestTime = System.currentTimeMillis();
            }

            requestCount++;
        } finally {
            lock.unlock();
        }

        semaphore.acquire();
    }

    /**
     * Represents a document for introducing a product into circulation.
     */
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;

        // Getters and setters absent for shortness
    }

    /**
     * Represents product description.
     */
    public static class Description {
        private String participantInn;

        // Getters and setters absent for shortness
    }

    /**
     * Represents a product.
     */
    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

        // Getters and setters absent for shortness
    }

    /**
     * Utility class for JSON serialization.
     */
    private static class JsonUtils {
        static String toJson(Object object) {
            // In a real implementation, this would use a JSON library like Jackson or Gson
            // This is a simplified version for demonstration purposes
            return "{\"dummy\":\"json\"}";
        }
    }
}