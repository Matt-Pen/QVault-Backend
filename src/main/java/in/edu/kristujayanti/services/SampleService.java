package in.edu.kristujayanti.services;

import com.mongodb.client.*;
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
import io.vertx.core.http.HttpServerResponse;
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
import org.bson.types.ObjectId;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import redis.clients.jedis.Jedis;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.print.Doc;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class SampleService {
    JwtUtil jtil = new JwtUtil();

    Jedis jedis = new Jedis("localhost", 6379);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    secretclass srt = new secretclass();
    Vertx vertx = Vertx.vertx();
    HttpServer server = vertx.createHttpServer();
    String connectionString = srt.constr;
    MongoClient mongoClient = MongoClients.create(connectionString);
    MongoDatabase database = mongoClient.getDatabase("questpaper");
    MongoCollection<Document> users = database.getCollection("Users");
    MongoCollection<Document> wish = database.getCollection("wishlist");
//    MongoCollection<Document> tasks = database.getCollection("tasks");
//    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");


    public void handleupload2(RoutingContext ctx) {
        Vertx vertx = Vertx.vertx(); // Required if you're inside a non-verticle class
        System.out.println("upload called");
        // Common metadata fields
        String name = ctx.request().getFormAttribute("course");
        String courseid = ctx.request().getFormAttribute("id");
        String department = ctx.request().getFormAttribute("department");
        String courseName = ctx.request().getFormAttribute("program");
        String examTerm = ctx.request().getFormAttribute("term");
        String year = ctx.request().getFormAttribute("year");
        JsonObject job=new JsonObject();

        try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");

            // Use GridFS to store PDFs
            GridFSBucket gridFSBucket = GridFSBuckets.create(database);
            List<ObjectId> pdfIds = new ArrayList<>();

            for (FileUpload upload : ctx.fileUploads()) {
                try {
                    if (upload.contentType().equals("application/pdf")) {
                        Path filePath = Paths.get(upload.uploadedFileName());
                        try (InputStream pdfStream = Files.newInputStream(filePath)) {
                            ObjectId fileId = gridFSBucket.uploadFromStream(upload.fileName(), pdfStream);
                            pdfIds.add(fileId);
                        }
                    } else {
                        System.out.println("Skipping non-PDF file: " + upload.fileName());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Store metadata and reference to GridFS files
            MongoCollection<Document> collection = database.getCollection("qpimage");

            Document doc = new Document("course", name)
                    .append("courseid", courseid)
                    .append("department", department)
                    .append("program", courseName)
                    .append("term", examTerm)
                    .append("year", year)
                    .append("fileIds", pdfIds);  // Save GridFS file references

           InsertOneResult ins= collection.insertOne(doc);
           if(ins.wasAcknowledged()){
               job.put("message","success");
           }else{
               job.put("message","fail");
           }
           ctx.response().end(job.encode());
            System.out.println("uploaded maybe");

        } catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(500).end("Failed to save PDFs with GridFS");
        }
    }

    public void handleupload(RoutingContext ctx) {

        List<Binary> pdfList = new ArrayList<>();

        ctx.fileUploads().forEach(upload -> {
            try {
                if (upload.contentType().equals("application/pdf")) {
                    byte[] pdfBytes = Files.readAllBytes(Paths.get(upload.uploadedFileName()));
                    pdfList.add(new Binary(pdfBytes));
                } else {
                    System.out.println("Skipping non-PDF file: " + upload.fileName());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

// common metadata fields
        String name = ctx.request().getFormAttribute("course");
        String courseid = ctx.request().getFormAttribute("id");
        String department = ctx.request().getFormAttribute("department");
        String courseName = ctx.request().getFormAttribute("program");
        String examTerm = ctx.request().getFormAttribute("term");
        String year = ctx.request().getFormAttribute("year");


        try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");
            MongoCollection<Document> collection = database.getCollection("qpimage");

            Document doc = new Document("course", name)
                    .append("courseid", courseid)
                    .append("department", department)
                    .append("program", courseName)
                    .append("term", examTerm)
                    .append("year", year)
                    .append("files", pdfList); //array

            collection.insertOne(doc);

            ctx.response().end("Multiple pages uploaded and grouped into one document");
        } catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(500).end("Failed to save PDF");

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
            Document doc = collection.find(Filters.regex("subname", pattern)).first();


            if (doc == null) {
                ctx.response().setStatusCode(404).end("Image not found for coursename: " + coursename);
                return;
            }

            // Get image binary and content type
//            Binary imageBinary = doc.get("image", Binary.class);
            List<Binary> pdfs = (List<Binary>) doc.get("files");
            StringBuilder html = new StringBuilder("<html><body>");

            for (Binary bin : pdfs) {
                String base64PDF = Base64.getEncoder().encodeToString(bin.getData());
                html.append("<iframe src='data:application/pdf;base64,")
                        .append(base64PDF)
                        .append("' width='100%' height='600px' style='margin-bottom: 20px;'></iframe>");
            }

            html.append("</body></html>");

            ctx.response()
                    .putHeader("Content-Type", "text/html")
                    .end(html.toString());


//
//            JsonArray result = new JsonArray();
//            for (Binary bin : pdfs) {
//                String base64pdf = Base64.getEncoder().encodeToString(bin.getData());
//                result.add("data:image/jpeg;base64," + base64pdf);
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


    public void getqp2(RoutingContext ctx){
        ctx.response().setChunked(true);
        String coursename = ctx.request().getParam("subname");

        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");
            MongoCollection<Document> collection = database.getCollection("qpimage");

            // Find first document where coursename matches
//            Document doc = collection.find(new Document("subname", coursename)).first();
            Pattern pattern = Pattern.compile(coursename, Pattern.CASE_INSENSITIVE);
            Document doc = collection.find(Filters.regex("subname", pattern)).first();
            if (doc == null) {
                ctx.response().setStatusCode(404).end("Image not found for coursename: " + coursename);
                return;
            }


            ArrayList<Object> main = new ArrayList<>();
            for(Document docs: collection.find(Filters.regex("subname", pattern))){
                String subname=docs.getString("subname");
                String courseid=docs.getString("courseid");
                String coursename2=docs.getString("coursename");
                String term=docs.getString("term");
                String year=docs.getString("year");
                String sem=docs.getString("sem");
                ArrayList<Object> list1 = new ArrayList<>();
                ArrayList<Binary> pdfs = (ArrayList<Binary>) doc.get("files");

                list1.add(subname);
                list1.add(courseid);
                list1.add(coursename2);
                list1.add(term);
                list1.add(year);
                list1.add(sem);
                list1.add(pdfs);

                main.add(list1);

            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonOutput = gson.toJson(main);
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(jsonOutput);



        }catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(500).end("Failed to fetch image");
        }


    }
    public void usersign(RoutingContext ctx) {
        String email = ctx.request().getParam("email");
        String pass = ctx.request().getParam("password");
        String status = "";
        ctx.response().setChunked(true);
        Document docs = users.find().filter(Filters.eq("email", email)).first();

        if (docs != null) {
            status = "exist";

        } else {
            if (email.matches(".*\\d.*") && email.contains("kristujayanti.com")) {
                String role = "student";
                String hashpass = hashPassword(pass);
                Document doc = new Document("email", email).append("pass", hashpass).append("role", role);
                InsertOneResult ins = users.insertOne(doc);
                if (ins.wasAcknowledged()) {
                    status = "success";
                }
            } else if (email.contains("kristujayanti.com")) {
                String role = "teacher";
                String hashpass = hashPassword(pass);
                Document doc = new Document("email", email).append("pass", hashpass).append("role", role);
                InsertOneResult ins = users.insertOne(doc);
                if (ins.wasAcknowledged()) {
                    status = "success";
                }
            } else {
                status = "invalid";

            }

        }
        JsonObject job=new JsonObject().put("message",status);
        ctx.response().end(job.encode());


    }

    public void userlog(RoutingContext ctx) {


        String user = ctx.request().getParam("Email");
        String pwd = ctx.request().getParam("Password");
        String status = "failed";
        String dash = "";
        ctx.response().setChunked(true);

        Document doc=users.find(Filters.eq("email",user)).first();


        if(doc==null) {
            status = "failed";
        }else{
            String dbuser = doc.getString("email");
            String dbpass = doc.getString("pass");
            String dbrole = doc.getString("role");
            if (dbuser.equals(user)) {
                if (verifyPassword(pwd, dbpass)) {
                    status = "successfull";
                    if (dbrole.equals("student")) {
                        dash = "student";

                    } else if (dbrole.equals("teacher")) {
                        dash = "teacher";
                    }
                } else {
                    status = "password failed";
                }

            }
        }

        JsonObject job=new JsonObject();
        job.put("message",status).put("role",dash);


        ctx.response().putHeader("Content-Type","application/json").end(job.encode());
        System.out.println("IN END");
    }

        public String hashPassword (String rawPassword){
            return passwordEncoder.encode(rawPassword);
        }
        public boolean verifyPassword (String rawPassword, String hashedPassword){
            return passwordEncoder.matches(rawPassword, hashedPassword);
        }

    public int resetpass(RoutingContext ctx)
    {   ctx.response().setChunked(true);
        JsonObject job=new JsonObject();
        int set=0;
        String email=ctx.request().getParam("email");
        String entoken=ctx.request().getParam("code");
        String pass=ctx.request().getParam("newPassword");
        if(entoken==null){
            String token=generateID(6);
            System.out.println(token);
            setoken(token,email);
            sendtokenemail(token,email);
            job.put("message","sent");

            set=1;
        }
        if(set!=1){
//            System.out.println("Received token: " + entoken);
            String tokemail=getoken(entoken);
            if(tokemail==null){
                set=1;
                job.put("message","invalid");

            }else {
//            System.out.println("redis email"+tokemail);
                if (tokemail.equals(email) || set != 1) {
                    String hashpass = hashPassword(pass);
                    Bson filter = Filters.eq("email", email);
                    Bson update = Updates.set("pass", hashpass);
                    UpdateResult res = users.updateOne(filter, update);
                    if (res.wasAcknowledged()) {
                        job.put("message","success");

                        deltoken(entoken);
                    }
                } else {
                    job.put("message","invalid");

                }
            }
        }
        ctx.response().end(job.encode());
        return set;
    }

    public void delqp(RoutingContext ctx) {
        ctx.response().setChunked(true);
        String id = ctx.request().getParam("id");
        System.out.println("IN DELETE QP");
        JsonObject job=new JsonObject();
        try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");
            MongoCollection<Document> collection = database.getCollection("qpimage");
            ObjectId obid = new ObjectId(id);

            Document metadataDoc = collection.find(Filters.eq("_id", obid)).first();
            if (metadataDoc == null) {
                ctx.response().setStatusCode(404).end("Document not found");
                return;
            }

            // Get the fileIds array
            List<ObjectId> fileIds = (List<ObjectId>) metadataDoc.get("fileIds");

            if (fileIds != null && !fileIds.isEmpty()) {
                GridFSBucket gridFSBucket = GridFSBuckets.create(database);
                for (ObjectId fid : fileIds) {
                    if (fid != null) {
                        gridFSBucket.delete(fid);
                    }
                }
            } else {
                System.out.println("fileIds missing or empty in document: " + metadataDoc.toJson());
            }

            DeleteResult del = collection.deleteOne(Filters.eq("_id", obid));
            if (del.wasAcknowledged()) {
               job.put("message","success");
            } else {
                job.put("message","failed");
            }

            ctx.response()
                    .putHeader("Content-Type", "application/json")  // 👈 important!
                    .end(job.encode());

        } catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(500).end("Failed to delete document");
        }
    }



    public void searchfilterpage(RoutingContext ctx) {
        String progname = ctx.request().getParam("program");

        if (progname != null) {
            try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
                MongoDatabase database = mongoClient.getDatabase("questpaper");
                MongoCollection<Document> collection = database.getCollection("qpimage");

                Pattern pattern = Pattern.compile(progname, Pattern.CASE_INSENSITIVE);
                List<Map<String, Object>> main = new ArrayList<>();

                for (Document doc : collection.find(Filters.regex("program", pattern))) {
                    Map<String, Object> paper = new LinkedHashMap<>();
                    paper.put("id",doc.getObjectId("_id").toHexString());
                    paper.put("course", doc.getString("course"));
                    paper.put("courseid", doc.getString("courseid"));
                    paper.put("department", doc.getString("department"));
                    paper.put("program", doc.getString("program"));
                    paper.put("term", doc.getString("term"));
                    paper.put("year", doc.getString("year"));

                    List<ObjectId> fileIdList = (List<ObjectId>) doc.get("fileIds");

                    if (fileIdList != null && !fileIdList.isEmpty()) {
                        // Use host from request (e.g. 192.168.1.107:8080)
                        String host = ctx.request().host();
                        String fileId = fileIdList.get(0).toHexString();
                        String pdfUrl = fileId;
                        paper.put("pdfUrl", pdfUrl);
                    } else {
                        paper.put("pdfUrl", null);
                    }

                    main.add(paper);
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String jsonOutput = gson.toJson(main);
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(jsonOutput);

            } catch (Exception e) {
                e.printStackTrace();
                ctx.response().setStatusCode(500).end("Search failed");
            }
        } else {
            ctx.response().setStatusCode(400).end("Missing program parameter");
        }
    }
    public void getPdfById(RoutingContext ctx) {
        String id = ctx.request().getParam("id");

        if (id == null || id.isEmpty()) {
            ctx.response().setStatusCode(400).end("Missing PDF id");
            return;
        }

        try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");
            GridFSBucket gridFSBucket = GridFSBuckets.create(database);

            ObjectId fileId = new ObjectId(id);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            gridFSBucket.downloadToStream(fileId, outputStream);

            byte[] pdfBytes = outputStream.toByteArray();

            ctx.response()
                    .putHeader("Content-Type", "application/pdf")
                    .putHeader("Content-Disposition", "inline; filename=\"questionpaper.pdf\"")
                    .putHeader("Content-Length", String.valueOf(pdfBytes.length))
                    .end(Buffer.buffer(pdfBytes)); // Vert.x Buffer from byte[]
        } catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(500).end("Failed to retrieve PDF");
        }
    }

    public void searchfilterpagefilter(RoutingContext ctx) {
        String course = ctx.request().getParam("course");
        String year1 = ctx.request().getParam("year");
        String term1 = ctx.request().getParam("term");
        System.out.println("hello search filter");
        Document filter = new Document();
        if (course != null) {
            filter.append("course", course);
        }
        if (year1 != null) {
            filter.append("year", year1);
        }
        if (term1 != null) {
            filter.append("term", term1);
        }

        try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
            MongoDatabase database = mongoClient.getDatabase("questpaper");
            MongoCollection<Document> collection = database.getCollection("qpimage");

            FindIterable<Document> results = collection.find(filter);
            if (!results.iterator().hasNext()) {
                ctx.response().setStatusCode(404).end("No matching documents found.");
                return;
            }

            List<Map<String, Object>> main = new ArrayList<>();

            for (Document docs : results) {
                Map<String, Object> list1 = new LinkedHashMap<>();
                list1.put("id",docs.getObjectId("_id").toHexString());
                list1.put("course", docs.getString("course"));
                list1.put("courseid", docs.getString("courseid"));
                list1.put("department", docs.getString("department"));
                list1.put("program", docs.getString("program"));
                list1.put("term", docs.getString("term"));
                list1.put("year", docs.getString("year"));

                // Extract GridFS fileId from document
                List<ObjectId> fileIds = (List<ObjectId>) docs.get("fileIds");
                if (fileIds != null && !fileIds.isEmpty()) {
                    String host = ctx.request().host(); // e.g., 192.168.1.107:8080
                    String pdfUrl = fileIds.get(0).toHexString();
                    list1.put("pdfUrl", pdfUrl);
                } else {
                    list1.put("pdfUrl", null);
                }

                main.add(list1);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonOutput = gson.toJson(main);
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(jsonOutput);

        } catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(500).end("Search failed");
        }
    }

    public void teacherpage(RoutingContext ctx){

        {
            try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
                MongoDatabase database = mongoClient.getDatabase("questpaper");
                MongoCollection<Document> collection = database.getCollection("qpimage");


                List<Map<String, Object>> main = new ArrayList<>();

                for (Document doc : collection.find()) {
                    Map<String, Object> paper = new LinkedHashMap<>();
                    paper.put("id",doc.getObjectId("_id").toHexString());
                    paper.put("course", doc.getString("course"));
                    paper.put("courseid", doc.getString("courseid"));
                    paper.put("department", doc.getString("department"));
                    paper.put("program", doc.getString("program"));
                    paper.put("term", doc.getString("term"));
                    paper.put("year", doc.getString("year"));

                    List<ObjectId> fileIdList = (List<ObjectId>) doc.get("fileIds");

                    if (fileIdList != null && !fileIdList.isEmpty()) {
                        // Use host from request (e.g. 192.168.1.107:8080)
                        String host = ctx.request().host();
                        String fileId = fileIdList.get(0).toHexString();
                        String pdfUrl = fileId;
                        paper.put("pdfUrl", pdfUrl);
                    } else {
                        paper.put("pdfUrl", null);
                    }

                    main.add(paper);
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String jsonOutput = gson.toJson(main);
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(jsonOutput);

            } catch (Exception e) {
                e.printStackTrace();
                ctx.response().setStatusCode(500).end("Search failed");
            }

    }
}
    public void wishlistadd(RoutingContext ctx){
        String email=ctx.request().getParam("email");
        String cid=ctx.request().getParam("id");
        JsonObject job=new JsonObject();

        Document doc=new Document().append("email",email).append("id",cid);
        Bson filter2 = Filters.and(Filters.eq("email", email), Filters.eq("id",cid));

        Document docs=wish.find().filter(filter2).first();
        if(docs!=null){
            job.put("message","exist");
            ctx.response().end(job.encode());
            return;
        }

        InsertOneResult ins=wish.insertOne(doc);
        if(ins.wasAcknowledged()){
            job.put("message","success");
        }else{
            job.put("message","failed");
        }
        ctx.response().end(job.encode());

    }

    public void wishlistget(RoutingContext ctx) {
        String email = ctx.request().getParam("email");
        ctx.response().setChunked(true);
        JsonObject job = new JsonObject();

        if (email != null) {
            try (MongoClient mongoClient = MongoClients.create(srt.constr)) {
                MongoDatabase database = mongoClient.getDatabase("questpaper");
                MongoCollection<Document> wish = database.getCollection("wishlist"); // Make sure this is the correct collection name
                MongoCollection<Document> collection = database.getCollection("qpimage");

                Pattern pattern = Pattern.compile(email, Pattern.CASE_INSENSITIVE);
                Document doc = wish.find(Filters.regex("email", pattern)).first();

                if (doc == null) {
                    job.put("message", "not found");
                    ctx.response().end(job.encode());
                    return;
                }

                List<Map<String, Object>> main = new ArrayList<>();

                for (Document docc : wish.find(Filters.regex("email", pattern))) {
                    String cid = docc.getString("id");
                    Document docs = collection.find(Filters.eq("_id", new ObjectId(cid))).first();

                    if (docs != null) {
                        Map<String, Object> paper = new LinkedHashMap<>();
                        paper.put("id", docs.getObjectId("_id").toHexString());
                        paper.put("course", docs.getString("course"));
                        paper.put("courseid", docs.getString("courseid"));
                        paper.put("department", docs.getString("department"));
                        paper.put("program", docs.getString("program"));
                        paper.put("term", docs.getString("term"));
                        paper.put("year", docs.getString("year"));

                        List<ObjectId> fileIdList = (List<ObjectId>) docs.get("fileIds");

                        if (fileIdList != null && !fileIdList.isEmpty()) {
                            String fileId = fileIdList.get(0).toHexString();
                            paper.put("pdfUrl", fileId);
                        } else {
                            paper.put("pdfUrl", null);
                        }

                        main.add(paper);
                    }
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String jsonOutput = gson.toJson(main);
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(jsonOutput);

            } catch (Exception e) {
                e.printStackTrace();
                ctx.response().setStatusCode(500).end("Search failed");
            }
        } else {
            ctx.response().setStatusCode(400).end("Missing email parameter");
        }
    }
    public void wishlistdel(RoutingContext ctx){
        String email=ctx.request().getParam("email");
        String cid=ctx.request().getParam("id");
        JsonObject job=new JsonObject();

        Document doc=new Document().append("email",email).append("id",cid);

        DeleteResult ins=wish.deleteOne(doc);
        if(ins.wasAcknowledged()){
            job.put("message","success");
        }else{
            job.put("message","failed");
        }
        ctx.response().end(job.encode());

    }






    public static String generateID(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }

    public void sendtokenemail(String token,String email){
        String to = email;
        // provide sender's email ID
        String from = srt.from;

        // provide Mailtrap's username
        final String username = srt.username;
        final String password = srt.password;

        // provide Mailtrap's host address
        String host = "smtp.gmail.com";

        // configure Mailtrap's SMTP details
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");

        // create the Session object
        Session session = Session.getInstance(props,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            // create a MimeMessage object
            Message message = new MimeMessage(session);
            // set From email field
            message.setFrom(new InternetAddress(from));
            // set To email field
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            // set email subject field
            message.setSubject("Use this token to reset your password");
            // set the content of the email message
            String htmlContent =  "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "  <body style=\"font-family: Arial, sans-serif; padding: 20px; background-color: #ffffff;\">\n" +
                    "   \n" +
                    "    <!-- Logo -->\n" +
                    "    <div style=\"text-align: center; margin-bottom: 20px;\">\n" +
                    "      <img src=\"https://i.postimg.cc/QdKHj2Wp/Screenshot-2025-07-15-110351.png\n\" alt=\"Qvault Logo\" width=\"400\" height=\"225\"/>\n" +
                    "    </div>\n" +
                    "\n" +
                    "    <!-- Heading -->\n" +
                    "    <h2 style=\"color: #333;\">Reset Your Password</h2>\n" +
                    "\n" +
                    "    <!-- Message -->\n" +
                    "    <p style=\"font-size: 15px;\">Hi there,</p>\n" +
                    "    <p style=\"font-size: 15px;\">You recently requested to reset your password. Please use the verification code below:</p>\n" +
                    "\n" +
                    "    <!-- Token Box -->\n" +
                    "    <div style=\"\n" +
                    "      text-align: center;\n" +
                    "      font-size: 26px;\n" +
                    "      font-weight: bold;\n" +
                    "      background: #f4f4f4;\n" +
                    "      border-radius: 8px;\n" +
                    "      padding: 14px;\n" +
                    "      width: fit-content;\n" +
                    "      margin: 20px auto;\n" +
                    "      color: #0066cc;\n" +
                    "      font-family: 'Courier New', Courier, monospace;\n" +
                    "      letter-spacing: 2px;\n" +
                    "    \">\n" +
                          token +
                    "    </div>\n" +
                    "\n" +
                    "    <!-- Expiry -->\n" +
                    "    <p style=\"color: red; font-weight: bold;\">Token is only valid for 10 Minutes.</p>\n" +
                    "\n" +
                    "    <!-- Ignore note -->\n" +
                    "    <p style=\"font-size: 14px; color: #555;\">\n" +
                    "      If you did not request a password reset, you can safely ignore this email.\n" +
                    "    </p>\n" +
                    "\n" +
                    "    <!-- Footer -->\n" +
                    "    <hr style=\"margin-top: 40px; border: none; border-top: 1px solid #ccc;\" />\n" +
                    "    <p style=\"font-size: 13px; color: #888;\">\n" +
                    "      Regards,<br />\n" +
                    "      <strong>The Qvault Team</strong><br />\n" +
                    "      © 2025 Qvault Inc. All rights reserved.\n" +
                    "    </p>\n" +
                    "  </body>\n" +
                    "</html>";

            message.setContent(htmlContent, "text/html");

            // send the email message
            Transport.send(message);

            System.out.println("Email Message token Sent Successfully!");

        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }

    }




    public void setoken(String key, String value)
    {
        jedis.setex(key,600,value);
    }
    public String getoken(String token){
        return jedis.get(token);
    }
    public void deltoken(String key){
        jedis.del(key);
    }
        //Your Logic Goes Here
    }

