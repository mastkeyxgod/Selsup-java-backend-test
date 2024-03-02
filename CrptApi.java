
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private HttpClient httpClient;
    private DocumentSerializer serializer;
    private ExecutorService executorService;
    private TimeLimiter limiter;
    private Queue<Request> requestQueue;
    private ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = new IsmpCrptClient();
        this.serializer = new ObjectMapperDocumentSerializer();
        this.limiter = new TimeLimiter(requestLimit, timeUnit.toMillis(1));
        this.requestQueue = new ConcurrentLinkedQueue<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        scheduleRequestProcessing();
    }

    public void createDocument(DocumentDTO documentDTO, String signature)  {
        if (limiter.allowRequest()) {
            String json = serializer.serialize(documentDTO);
            httpClient.createDocument(json, signature);
        } else {
            requestQueue.add(new Request(documentDTO, signature));
        }
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 1);
        crptApi.createDocument(new DocumentDTO(), "test");
        crptApi.createDocument(new DocumentDTO(), "test");
        crptApi.createDocument(new DocumentDTO(), "test");

    }

    private void processQueue() {
        while (!requestQueue.isEmpty()) {
            Request request = requestQueue.poll();
            DocumentDTO documentDTO = request.getDocumentDTO();
            String signature = request.getSignature();
            createDocument(documentDTO, signature);
        }
    }
    private void scheduleRequestProcessing() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!requestQueue.isEmpty()) {
                processQueue();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
}

class Request {
    private DocumentDTO documentDTO;
    private String signature;

    public Request(DocumentDTO documentDTO, String signature) {
        this.documentDTO = documentDTO;
        this.signature = signature;
    }

    public DocumentDTO getDocumentDTO() {
        return documentDTO;
    }

    public String getSignature() {
        return signature;
    }
}

class TimeLimiter {
    private int requestLimit;
    private AtomicInteger requestCounter;
    private long timeIntervalMillis;
    private long lastResetTime;

    public TimeLimiter(int requestLimit, long timeIntervalMillis) {
        this.requestLimit = requestLimit;
        this.requestCounter = new AtomicInteger(0);
        this.timeIntervalMillis = timeIntervalMillis;
        this.lastResetTime = System.currentTimeMillis();
    }

    public synchronized boolean allowRequest() {
        resetIfNecessary();
        return requestCounter.incrementAndGet() <= requestLimit;
    }

    private synchronized void resetIfNecessary() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetTime >= timeIntervalMillis) {
            requestCounter.set(0);
            lastResetTime = currentTime;
        }
    }
}

interface DocumentSerializer {
    String serialize(DocumentDTO documentDTO);
}

class  ObjectMapperDocumentSerializer implements DocumentSerializer {
    public String serialize(DocumentDTO documentDTO) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(documentDTO);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Can't serialize document", e);
        }
    }
}

interface HttpClient {
    void createDocument(String json, String signature);
}

class IsmpCrptClient implements HttpClient {
    private final String BASE_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    @Override
    public void createDocument(String json, String signature) {
        HttpPost httpPost = new HttpPost(BASE_URL);
        StringEntity entity;
        try {
            entity = new StringEntity(json);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported encoding");
        }
        httpPost.setEntity(entity);
        httpPost.setHeader("Authorization", signature);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            CloseableHttpResponse response = client.execute(httpPost);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}



class ProductDTO {
    private String certificate_document;
    private String certificate_document_date;
    private String certificate_document_number;
    private String owner_inn;
    private String producer_inn;
    private String production_date;
    private String tnved_code;
    private String uit_code;
    private String uitu_code;

    public ProductDTO() {
    }

    public ProductDTO(String certificate_document, String certificate_document_date,
                      String certificate_document_number, String owner_inn, String producer_inn,
                      String production_date, String tnved_code, String uit_code, String uitu_code) {
        this.certificate_document = certificate_document;
        this.certificate_document_date = certificate_document_date;
        this.certificate_document_number = certificate_document_number;
        this.owner_inn = owner_inn;
        this.producer_inn = producer_inn;
        this.production_date = production_date;
        this.tnved_code = tnved_code;
        this.uit_code = uit_code;
        this.uitu_code = uitu_code;

    }

    public String getCertificate_document() {
        return certificate_document;
    }

    public String getCertificate_document_date() {
        return certificate_document_date;
    }

    public String getCertificate_document_number() {
        return certificate_document_number;
    }

    public String getOwner_inn() {
        return owner_inn;
    }

    public String getProducer_inn() {
        return producer_inn;
    }

    public String getProduction_date() {
        return production_date;
    }

    public String getTnved_code() {
        return tnved_code;
    }

    public String getUit_code() {
        return uit_code;
    }

    public String getUitu_code() {
        return uitu_code;
    }
}

class DocumentDTO {
    private String doc_id;
    private String doc_status;
    private String doc_type;
    private boolean importRequest;
    private String owner_inn;
    private String participant_inn;
    private String producer_inn;
    private String production_date;
    private String production_type;
    private List<ProductDTO> products;
    private String reg_date;
    private String reg_number;

    public DocumentDTO() {
    }

    public DocumentDTO(String doc_id, String doc_status, String doc_type, boolean importRequest,
                       String owner_inn, String participant_inn, String producer_inn,
                       String production_date, String production_type, List<ProductDTO> products,
                       String reg_date, String reg_number) {
        this.doc_id = doc_id;
        this.doc_status = doc_status;
        this.doc_type = doc_type;
        this.importRequest = importRequest;
        this.owner_inn = owner_inn;
        this.participant_inn = participant_inn;
        this.producer_inn = producer_inn;
        this.production_date = production_date;
        this.production_type = production_type;
        this.products = products;
        this.reg_date = reg_date;
        this.reg_number = reg_number;
    }

    public String getDoc_id() {
        return doc_id;
    }

    public String getDoc_status() {
        return doc_status;
    }

    public String getDoc_type() {
        return doc_type;
    }

    public boolean isImportRequest() {
        return importRequest;
    }

    public String getOwner_inn() {
        return owner_inn;
    }

    public String getParticipant_inn() {
        return participant_inn;
    }

    public String getProducer_inn() {
        return producer_inn;
    }

    public String getProduction_date() {
        return production_date;
    }

    public String getProduction_type() {
        return production_type;
    }

    public List<ProductDTO> getProducts() {
        return products;
    }

    public String getReg_date() {
        return reg_date;
    }

    public String getReg_number() {
        return reg_number;
    }
}






