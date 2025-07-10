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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
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
        String pass = ctx.request().getParam("pass");
        String status = "";
        ctx.response().setChunked(true);
        Document docs = users.find().filter(Filters.eq("email", email)).first();

        if (docs != null) {
            status = "Email already exist";

        } else {
            if (email.matches(".*\\d.*") && email.contains("kristujayanti.com")) {
                String role = "student";
                String hashpass = hashPassword(pass);
                Document doc = new Document("email", email).append("pass", hashpass).append("role", role);
                InsertOneResult ins = users.insertOne(doc);
                if (ins.wasAcknowledged()) {
                    status = "Signed in successfully, please proceed to login";
                }
            } else if (email.contains("kristujayanti.com")) {
                String role = "teacher";
                String hashpass = hashPassword(pass);
                Document doc = new Document("email", email).append("pass", hashpass).append("role", role);
                InsertOneResult ins = users.insertOne(doc);
                if (ins.wasAcknowledged()) {
                    status = "Signed in successfully, please proceed to login";
                }
            } else {
                status = "Invalid Email";

            }

        }
        ctx.response().end(status);


    }

    public void userlog(RoutingContext ctx) {

        System.out.println("chandu here");
        String user = ctx.request().getParam("Email");
        String pwd = ctx.request().getParam("Password");
        String status = "failed";
        String dash = "";
        ctx.response().setChunked(true);

        Document doc=users.find(Filters.eq("email",user)).first();
            System.out.println("Chandu here");

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
                        System.out.println("in student");
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
        int set=0;
        String email=ctx.request().getParam("email");
        String entoken=ctx.request().getParam("token");
        String pass=ctx.request().getParam("pass");
        if(entoken==null){
            String token=generateID(6);
            setoken(token,email);
            sendtokenemail(token,email);
            ctx.response().write("Password reset token sent to Email.\n Token only valid for 10 Minutes");
            set=1;
        }
        if(set!=1){
//            System.out.println("Received token: " + entoken);
            String tokemail=getoken(entoken);
            if(tokemail==null){
                set=1;
                ctx.response().write("Invalid token.");
            }else {
//            System.out.println("redis email"+tokemail);
                if (tokemail.equals(email) || set != 1) {
                    String hashpass = hashPassword(pass);
                    Bson filter = Filters.eq("email", email);
                    Bson update = Updates.set("pass", hashpass);
                    UpdateResult res = users.updateOne(filter, update);
                    if (res.wasAcknowledged()) {
                        ctx.response().write("Password successfully changed.");
                        deltoken(entoken);
                    }
                } else {
                    ctx.response().write("invalid token or token has expired");
                }
            }
        }
        ctx.response().end();
        return set;
    }

    public void searchfilter(RoutingContext ctx){
       String subname=ctx.request().getParam("subject");
       String courseid=ctx.request().getParam("courseid");
       String coursename=ctx.request().getParam("coursename");
       String term=ctx.request().getParam("term");
       String year=ctx.request().getParam("year");
       String sem=ctx.request().getParam("sem");


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
                    "      <img src=\"https://i.postimg.cc/dVLPY53r/new.png\n\" alt=\"Qvault Logo\" width=\"400\" height=\"225\"/>\n" +
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
                    "      TBlh1X\n" +
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

