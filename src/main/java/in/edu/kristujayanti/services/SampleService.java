package in.edu.kristujayanti.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import in.edu.kristujayanti.JwtUtil;
import in.edu.kristujayanti.secretclass;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import redis.clients.jedis.Jedis;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class SampleService {
    JwtUtil jtil=new JwtUtil();

    Jedis jedis = new Jedis("localhost", 6379);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    secretclass srt=new secretclass();
    Vertx vertx = Vertx.vertx();
    HttpServer server = vertx.createHttpServer();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
//    MongoDatabase database = mongoClient.getDatabase("To-do-list");
//    MongoCollection<Document> users = database.getCollection("Users");
//    MongoCollection<Document> tasks = database.getCollection("tasks");
//    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

//    public void handleupload(RoutingContext ctx){
//        ctx.fileUploads().forEach(upload -> {
//            String uploadedFilePath = upload.uploadedFileName();
//            String originalFileName = upload.fileName();
//
//            // Get extra fields from form-data (query param or request param)
//            String name = ctx.request().getFormAttribute("subname");
//            String courseid = ctx.request().getFormAttribute("id");
//            String department = ctx.request().getFormAttribute("department");
//            String courseName = ctx.request().getFormAttribute("coursename");
//            String examTerm = ctx.request().getFormAttribute("term");
//            String year = ctx.request().getFormAttribute("year");
//            String sem = ctx.request().getFormAttribute("sem");
//
//            try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017");
//                 FileInputStream stream = new FileInputStream(uploadedFilePath)) {
//
//                MongoDatabase database = mongoClient.getDatabase("questpaper");
//                GridFSBucket gridFSBucket = GridFSBuckets.create(database, "exam_images");
//
//                // Custom metadata with the image
//                Document metadata = new Document("subname", name)
//                        .append("courseid",courseid)
//                        .append("department", department)
//                        .append("coursename", courseName)
//                        .append("term", examTerm)
//                        .append("year",year)
//                        .append("sem",sem)
//                        .append("content_type", upload.contentType());
//
//                GridFSUploadOptions options = new GridFSUploadOptions().metadata(metadata);
//
//                gridFSBucket.uploadFromStream(originalFileName, stream, options);
//
//                ctx.response().end("Image with metadata uploaded successfully!");
//
//            } catch (IOException e) {
//                e.printStackTrace();
//                ctx.response().setStatusCode(500).end("Upload failed");
//            }
//        });
//
//    }


    public void handleupload(RoutingContext ctx) {

        List<Binary> imageList = new ArrayList<>();

        ctx.fileUploads().forEach(upload -> {
            try {
                byte[] imageBytes = Files.readAllBytes(Paths.get(upload.uploadedFileName()));
                imageList.add(new Binary(imageBytes));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

// common metadata fields
        String name = ctx.request().getFormAttribute("subname");
        String courseid = ctx.request().getFormAttribute("id");
        String department = ctx.request().getFormAttribute("department");
        String courseName = ctx.request().getFormAttribute("coursename");
        String examTerm = ctx.request().getFormAttribute("term");
        String year = ctx.request().getFormAttribute("year");
        String sem = ctx.request().getFormAttribute("sem");

        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");
            MongoCollection<Document> collection = database.getCollection("qpimage");

            Document doc = new Document("subname", name)
                    .append("courseid", courseid)
                    .append("department", department)
                    .append("coursename", courseName)
                    .append("term", examTerm)
                    .append("year", year)
                    .append("sem", sem)
                    .append("images", imageList); //array

            collection.insertOne(doc);

            ctx.response().end("Multiple pages uploaded and grouped into one document");
        }

    }

    public void getqp(RoutingContext ctx) {
        ctx.response().setChunked(true);
        String coursename = ctx.request().getParam("subname");

        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");
            MongoCollection<Document> collection = database.getCollection("qpimage");

            // Find first document where coursename matches
//            Document doc = collection.find(new Document("subname", coursename)).first();
            Pattern pattern = Pattern.compile(coursename, Pattern.CASE_INSENSITIVE);
            Document doc=collection.find(Filters.regex("subname",pattern)).first();


            if (doc == null) {
                ctx.response().setStatusCode(404).end("Image not found for coursename: " + coursename);
                return;
            }

            // Get image binary and content type
//            Binary imageBinary = doc.get("image", Binary.class);
            List<Binary> images = (List<Binary>) doc.get("images");
            String contentType = doc.getString("content_type");

            // Set response headers and send image

            StringBuilder html = new StringBuilder("<html><body>");

            for (Binary bin : images) {
                String base64Image = Base64.getEncoder().encodeToString(bin.getData());
                html.append("<img src='data:").append(contentType).append(";base64,")
                        .append(base64Image).append("' style='width:100%; display:block; margin-bottom:20px;'/>");
            }

            html.append("</body></html>");

            ctx.response()
                    .putHeader("Content-Type", "text/html")
                    .end(html.toString());

//            JsonArray result = new JsonArray();
//            for (Binary bin : images) {
//                String base64Image = Base64.getEncoder().encodeToString(bin.getData());
//                result.add("data:image/jpeg;base64," + base64Image);
//            }
//
//            ctx.response()
//                    .putHeader("Content-Type", "application/json")
//                    .end(result.encodePrettily());

        } catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(500).end("Failed to fetch image");
        }


    }





    //Your Logic Goes Here
}
