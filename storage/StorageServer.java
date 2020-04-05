package storage;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import jsonhelper.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import test.common.Path;

public class StorageServer {

    /** Storage IP address. */
    public final static String      STORAGE_IP = "127.0.0.1";
    private static int CLIENT_PORT;
    private static int COMMAND_PORT;
    private static int REGISTRATION_PORT;
    private static String ROOT_DIR;
    private static File directory;
    /** Client interface skeleton. */
    protected HttpServer            client_skeleton;
    /** Command interface skeleton. */
    protected HttpServer            command_skeleton;
    /** Indicates that the skeletons have been started. */
    private boolean                 skeletons_started;
    /** Gson object which can parse json to an object. */
    protected Gson                  gson;

    public StorageServer()
    {
        directory = new File(ROOT_DIR);
        skeletons_started = false;
        gson = new Gson();
    }

    /** Starts skeletons for the client and command interfaces.
     */
    protected synchronized void startSkeletons() throws IOException {
        // Prevent repeated starting of the skeletons and re-creation of stubs.
        if(skeletons_started)
            return;

        this.client_skeleton = HttpServer.create(new InetSocketAddress(CLIENT_PORT), 0);
        this.client_skeleton.setExecutor(Executors.newCachedThreadPool());

        this.command_skeleton = HttpServer.create(new InetSocketAddress(COMMAND_PORT), 0);
        this.command_skeleton.setExecutor(Executors.newCachedThreadPool());

        // Start the client interface skeleton and create the stub.
        client_skeleton.start();

        // Start the registration skeleton and create the stub.
        command_skeleton.start();

        skeletons_started = true;

        // Register all API to two skeletons
        this.add_client_api();
        this.add_command_api();
    }

    /** Register to a naming server
     */
    public HttpResponse<String> register(Gson gson, String[] files)
    {

        RegisterRequest registerRequest = new RegisterRequest(STORAGE_IP, CLIENT_PORT, COMMAND_PORT, files);

        HttpResponse<String> response = null;

        try{
            response = this.getResponse(STORAGE_IP, REGISTRATION_PORT, "/register", registerRequest);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return response;
    }

    public synchronized void start() throws IOException {
        // Start storage server skeletons.
        Path[] result = Path.list(directory);
        String[] allFiles = new String[result.length];
        for (int i = 0; i < result.length; i++) allFiles[i] = result[i].toString();

        // Register the storage server with the naming server.
        HttpResponse<String> response = register(gson, allFiles);

        allFiles = gson.fromJson(response.body(), FilesReturn.class).files;
        for (String eachpath : allFiles){
            File file = new File(directory, eachpath);
            file.delete();
        }
        prune(directory);

        // MAKE SURE TO DELETE THE FILE BEFORE CREATING AND STARTING THE SERVERS!!!
        startSkeletons();
    }

    /** Add APIs supported by client skeleton. */
    private void add_client_api()
    {
        this.size();
        this.read();
        this.write();
    }

    /** Add APIs supported by command skeleton. */
    private void add_command_api()
    {
        this.create();
        this.delete();
        this.copy();
    }

    public void size()
    {
        this.client_skeleton.createContext("/storage_size", (exchange ->
        {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                PathRequest pr = null;
                SizeReturn sr = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pr = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                String path = pr.path;
                if (path == null || path.length() == 0){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                File file = new File(directory, path);
                if (!file.exists() || file.isDirectory()){
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                sr = new SizeReturn(file.length());
                respText = gson.toJson(sr);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }

    public void read()
    {
        this.client_skeleton.createContext("/storage_read", (exchange ->
        {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                ReadRequest rr = null;
                DataReturn dr = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    rr = gson.fromJson(isr, ReadRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText,returnCode);
                    return;
                }

                String path = rr.path;
                long offset = rr.offset;
                int length = rr.length;

                if (path == null || path.length() == 0){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                File file = new File(directory, path);
                if (!file.exists() || file.isDirectory()){
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                if (file.length() == 0){
                    dr = new DataReturn("");
                    respText = gson.toJson(dr);
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                if (offset < 0 || length > file.length() || length < 0 || offset == file.length() ){
                    this.sendExceptionReturn(exchange, "IndexOutOfBoundsException", 400);
                    return;
                }

                byte[] b = new byte[length];
                RandomAccessFile reader = new RandomAccessFile(file, "r");
                reader.seek(offset);
                reader.read(b);
                String base64String = Base64.getEncoder().encodeToString(b);
                reader.close();
                dr = new DataReturn(base64String);
                respText = gson.toJson(dr);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }

    public void write()
    {
        this.client_skeleton.createContext("/storage_write", (exchange ->
        {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                WriteRequest wr = null;
                BooleanReturn br = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    wr = gson.fromJson(isr, WriteRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                String path = wr.path;
                long offset = wr.offset;
                String data = wr.data;
                if (offset < 0){
                    this.sendExceptionReturn(exchange, "IndexOutOfBoundsException", 400);
                    return;
                }
                if (path == null || path.length() == 0){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }

                File file = new File(directory, path);
                if (!file.exists() || file.isDirectory()){
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                // use what the Internet told me
                RandomAccessFile writer = new RandomAccessFile(file, "rw");
                writer.seek(offset);
                byte[] b = Base64.getDecoder().decode(data);
                writer.write(b);
                writer.close();
                br = new BooleanReturn(true);
                respText = gson.toJson(br);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }

    public void create()
    {
        this.command_skeleton.createContext("/storage_create", (exchange ->
        {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                PathRequest pr = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pr = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                if (pr.path.equals("") || pr.path.charAt(0) != '/' || pr.path.contains(":")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                String path = pr.path;
                File file = new File(directory, path);
                if (path.equals("/") || file.exists()){
                    this.sendBooleanReturn(exchange, false, returnCode);
                    return;
                }
                String[] components = eliminate_spaces(path);
                String create_path = ROOT_DIR;
                for (int i = 0 ; i < components.length; i++){
                    create_path += ("/" + components[i]);
                    File newFile = new File(create_path);
                    if (i != components.length - 1) newFile.mkdir();
                    else newFile.createNewFile();
                }
                this.sendBooleanReturn(exchange, true, returnCode);
            }
        }));
    }

    public void delete()
    {
        this.command_skeleton.createContext("/storage_delete", (exchange ->
        {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                PathRequest pr = null;
                ExceptionReturn er = null;
                BooleanReturn br = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pr = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                String path = pr.path;
                if (path.length() == 0){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                if (path.equals("/")){
                    this.sendBooleanReturn(exchange, false, returnCode);
                    return;
                }
                File file = new File(directory, path);
                if (!file.exists()){
                    this.sendBooleanReturn(exchange, false, returnCode);
                    return;
                }
                if (file.isFile()){
                    file.delete();
                }else{
                    deletion(file);
                    file.delete();
                }
                this.sendBooleanReturn(exchange, true, returnCode);
            }
        }));
    }

    public void copy()
    {
        this.command_skeleton.createContext("/storage_copy", (exchange ->
        {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                CopyRequest cr = null;
                ExceptionReturn er = null;
                BooleanReturn br = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    cr = gson.fromJson(isr, CopyRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                String path = cr.path;
                String ip = cr.server_ip;
                int port = cr.server_port;
                if (path.equals("")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                File file = new File(directory, path);
                long size = 0;
                PathRequest pr = new PathRequest(path);
                try{
                    HttpResponse<String> response = this.getResponse(ip, port, "/storage_size", pr);
                    if (gson.fromJson(response.body(), ExceptionReturn.class).exception_type != null){
                        this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                        return;
                    }
                    size = gson.fromJson(response.body(), SizeReturn.class).size;
                }catch(Exception e){
                    System.out.println(e.getMessage());
                }
                ReadRequest rr = new ReadRequest(path, 0, (int) size);
                String data = "";
                try{
                    HttpResponse<String> response2 = this.getResponse(ip, port, "/storage_read", rr);
                    if (gson.fromJson(response2.body(), ExceptionReturn.class).exception_type != null){
                        this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                        return;
                    }
                    data = gson.fromJson(response2.body(), DataReturn.class).data;
                }catch(Exception e){
                    System.out.println(e.getMessage());
                }
                // 一开始不对的原因是因为没有理解对 nonexistent file 和 dir不存在说的是
                // 说的是hosting file的那个server上的 而不是当前这个的！
                // 其次就是send req那里 ip 和 port的URI没构建对 恨不应该！
                if (!file.exists()){
                    String[] components = eliminate_spaces(path);
                    String create_path = ROOT_DIR;
                    // making new dir and new files
                    for (int i = 0 ; i < components.length; i++){
                        create_path += ("/" + components[i]);
                        File newFile = new File(create_path);
                        if (i != components.length - 1) newFile.mkdir();
                        else newFile.createNewFile();
                    }
                }
                RandomAccessFile writer = new RandomAccessFile(file, "rw");
                // 这个地方要刷新 抹掉之前的所有内容 方法就是setlength = 0，要不然之前的data
                // 会被往后挤。之前的文件写完了，length就固定了，没法改了！
                writer.setLength(0);
                writer.seek(0);
                byte[] b = Base64.getDecoder().decode(data);
                writer.write(b);
                writer.close();
                this.sendBooleanReturn(exchange, true, returnCode);
            }
        }));
    }

    protected HttpResponse<String> getResponse(String ip, int port, String api, Object obj) throws IOException,
            InterruptedException{
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + ":" + port + api))
                .setHeader("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(obj)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected void sendBooleanReturn(HttpExchange exchange, boolean success, int returnCode) throws IOException
    {
        BooleanReturn booleanReturn = new BooleanReturn(success);
        String respText = gson.toJson(booleanReturn);
        this.generateResponseAndClose(exchange, respText, returnCode);
    }

    protected void sendExceptionReturn(HttpExchange exchange, String exception_type, int returnCode)
            throws IOException{
        ExceptionReturn er = new ExceptionReturn(exception_type, "");
        String respText = gson.toJson(er);
        this.generateResponseAndClose(exchange, respText, returnCode);
    }

    protected void sendNotPostRequestReturn(HttpExchange exchange, String respText, int returnCode)
            throws IOException {
        respText = "Error during parse JSON object!\n";
        returnCode = 400;
        this.generateResponseAndClose(exchange, respText, returnCode);
    }
    /**
     * call this function when you want to write to response and close the connection.
     */
    private void generateResponseAndClose(HttpExchange exchange, String respText, int returnCode)
            throws IOException {
        exchange.sendResponseHeaders(returnCode, respText.getBytes().length);
        OutputStream output = exchange.getResponseBody();
        output.write(respText.getBytes());
        output.flush();
        exchange.close();
    }
    /** take the given string path and eliminate the spaces after split **/
    public static String[] eliminate_spaces(String path){
        List<String> result = new ArrayList<>();
        String[] parts = path.split("/");
        for (String each : parts){
            if (!each.equals("")) result.add(each);
        }
        return result.toArray(new String[0]);
    }

    /** must prune all the empty directories
     notice some directory becomes empty when its sub directory
     is pruned !  **/
    private static void prune(File dir){
        File[] files = dir.listFiles();
        // 当前的dir是空的 但是不是root的时候（root可以空 嘻嘻）
        if (files.length == 0 && !dir.equals(directory)){
            dir.delete();
            return;
        }
        for (File file : files){
            // 不管file 只管dir
            if (file.isDirectory()){
                // 这个if用来应对root dir子文件夹empty的情况 和 line362不一回事儿
                if (file.listFiles().length == 0){
                    file.delete();
                }else{
                    prune(file);
                    // prune完了子文件夹如果当前文件夹成empty 那就prune掉自己
                    if (file.listFiles().length == 0){
                        file.delete();
                    }
                }
            }
        }
    }

    /** delete literally everything under the give dir **/
    private static void deletion(File dir){
        for (File each : dir.listFiles()){
            if (each.isFile()){
                each.delete();
            }else{
                // recursively delete each file or dir
                deletion(each);
                // handle the case when all the files for this dir are deleted. Delete the dir then.
                if (each.listFiles().length == 0) each.delete();
            }
        }
    }

    public static void main(String[] args) throws FileNotFoundException {
        if (args.length != 4){
            System.out.println("Proper Usage is: java storage.StorageServer port port port directoryAddr");
            System.exit(0);
        }
        CLIENT_PORT = Integer.parseInt(args[0]);
        COMMAND_PORT = Integer.parseInt(args[1]);
        REGISTRATION_PORT = Integer.parseInt(args[2]);
        ROOT_DIR = args[3];
        // using for debug purpose
        PrintStream debug_file = new PrintStream(new FileOutputStream("debug_storage.txt", true));
        System.setOut(debug_file);
        try{
            StorageServer ss = new StorageServer();
            ss.start();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}
