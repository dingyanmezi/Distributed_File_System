package naming;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jsonhelper.*;
import java.io.*;
import java.net.URI;
import java.util.List;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class NamingServer {

    private static int SERVICE_PORT;
    private static int REGISTRATION_PORT;
    /** Naming server registration interface skeleton. */
    private HttpServer registration_skeleton;
    /** Naming server service interface skeleton. */
    private HttpServer          service_skeleton;
    /** Last registered storage server client interface. */
    private ServerInfo client_stub = null;
    /** Last registered storage server command interface. */
    private ServerInfo          command_stub = null;
    /** Indicates that the skeleton has started. */
    private boolean             skeletons_started = false;
    /** Gson object which can parse json to an object. */
    protected Gson gson;
    private List<StorageInfo> registered_storages;
    private List<ServerInfo> registered_clients;
    private List<ServerInfo> registered_commands;
    /** maintain the lock for each file */
    private ConcurrentHashMap<Address, RWLock> address_lock_table;
    /** Creates the naming server.
     */
    NamingServer() throws IOException
    {
        /** create registration interface server with port */
        this.registration_skeleton = HttpServer.create(new InetSocketAddress(REGISTRATION_PORT), 0);
        this.registration_skeleton.setExecutor(Executors.newCachedThreadPool());
        /** create service interface server with port */
        this.service_skeleton = HttpServer.create(new InetSocketAddress(SERVICE_PORT), 0);
        this.service_skeleton.setExecutor(Executors.newCachedThreadPool());
        this.gson = new Gson();
        this.registered_clients = new ArrayList<>();
        this.registered_commands = new ArrayList<>();
        registered_storages = new ArrayList<>();
        this.address_lock_table = new ConcurrentHashMap<>();
        this.address_lock_table.put(Address.start, new RWLock());
    }

    void start()
    {
        this.startSkeletons();
    }

    /** Set up API comsumption methods and start servers*/
    private void startSkeletons() {
        // Prevent repeated starting of the skeletons and re-creation of stubs.
        if (this.skeletons_started) return;

        this.add_registration_api();
        this.registration_skeleton.start();
        this.add_service_api();
        this.service_skeleton.start();

        this.skeletons_started = true;
    }

    private void add_registration_api() {
        this.register();
    }

    private void add_service_api() {
        this.pathCheck();
        this.getStorage();
        this.delete();
        this.create_directory();
        this.create_file();
        this.list();
        this.isDirectory();
        this.lock();
        this.unlock();
    }

    private void pathCheck(){
        this.service_skeleton.createContext("/is_valid_path", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())){
                PathRequest pathRequest = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pathRequest = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange ,respText, returnCode);
                    return;
                }
                String filePath = pathRequest.path;
                if (filePath.equals("") || filePath.charAt(0) != '/' || filePath.contains(":")){
                    this.sendBooleanReturn(exchange, false, returnCode);
                    return;
                }
                this.sendBooleanReturn(exchange, true, returnCode);
            }
        }));
    }
    /** Get the client stub information of the file*/
    private void getStorage(){
        this.service_skeleton.createContext("/getstorage", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())){
                PathRequest pathRequest = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pathRequest = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText,returnCode);
                    return;
                }
                String file = pathRequest.path;
                if (file.equals("") || file.charAt(0) != '/' || file.contains(":")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                Address candi = new Address(file);
                if(!candi.exist() || candi.is_Directory()){
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }else{
                    ServerInfo si = new ServerInfo(candi.get_serverInfo().server_ip, candi.get_serverInfo().client_port);
                    respText = gson.toJson(si);
                    this.generateResponseAndClose(exchange, respText, returnCode);
                }
            }
        }));
    }
    /** delete the file including its copies on different storage servers*/
    private void delete(){
        this.service_skeleton.createContext("/delete", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                PathRequest pathRequest = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pathRequest = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                String each = pathRequest.path;
                if (each.equals("")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                if (each.equals("") || each.charAt(0) != '/' || each.contains(":")){
                    this.sendExceptionReturn(exchange, "IllegalStateException", 400);
                    return;
                }
                Address candi = new Address(each);
                if(!candi.exist()){
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                candi.delete();
                BooleanReturn br = new BooleanReturn(true);
                respText = gson.toJson(br);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }

    private void create_directory(){
        this.service_skeleton.createContext("/create_directory", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())){
                PathRequest pathRequest = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pathRequest = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange ,respText, returnCode);
                    return;
                }
                String file = pathRequest.path;
                if (file.equals("") || file.charAt(0) != '/' || file.contains(":")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                Address candi = new Address(file);
                BooleanReturn br;

                // given name already exist or not + check whether is dir
                if (file.equals("/")){
                    br = new BooleanReturn(false);
                } else if (candi.exist() && !candi.is_Directory()){
                    br = new BooleanReturn(false);
                } else if (candi.exist() && candi.is_Directory()){
                    br = new BooleanReturn(false);
                } else if (candi.create_directory()){
                    br = new BooleanReturn(true);
                }else{
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                respText = gson.toJson(br);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }

    private void create_file(){
        this.service_skeleton.createContext("/create_file", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())){
                PathRequest pathRequest = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pathRequest = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                String file = pathRequest.path;
                if (file.equals("") || file.charAt(0) != '/' || file.contains(":")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                Address candi = new Address(file);
                BooleanReturn br;

                // given name already exist or not
                if (file.equals("/")){
                    br = new BooleanReturn(false);
                } else if (candi.exist() && !candi.is_Directory()){
                    br = new BooleanReturn(false);
                } else if (candi.exist() && candi.is_Directory()){
                    br = new BooleanReturn(false);
                }else if (candi.create_file()){     // if file created successfully
                    br = new BooleanReturn(true);
                    PathRequest pr = new PathRequest(candi.toString());
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + command_stub.server_port + "/storage_create"))
                            .setHeader("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(pr)))
                            .build();
                    try{
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }else{
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                respText = gson.toJson(br);
                this.generateResponseAndClose(exchange, respText, returnCode);
            }
        }));
    }
    /** list all the files under one directory */
    private void list(){
        this.service_skeleton.createContext("/list", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())){
                PathRequest pathRequest = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pathRequest = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    respText = "Error during parse JSON object!\n";
                    returnCode = 400;
                    this.generateResponseAndClose(exchange, respText, returnCode);
                    return;
                }
                String file = pathRequest.path;
                if (file.equals("") || file.charAt(0) != '/' || file.contains(":")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                Address candi = new Address(file);
                if (!candi.exist() || !candi.is_Directory()){
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                List<String> result = candi.list_under_dir();
                FilesReturn fr = new FilesReturn(result.toArray(new String[0]));
                respText = gson.toJson(fr);
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;

            }
        }));
    }

    private void isDirectory(){
        this.service_skeleton.createContext("/is_directory", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())){
                PathRequest pr = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    pr = gson.fromJson(isr, PathRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                String file = pr.path;
                if (file.equals("") || file.charAt(0) != '/' || file.contains(":")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                Address candi = new Address(file);
                if (!candi.exist()){
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                BooleanReturn br = new BooleanReturn(candi.is_Directory());
                respText = gson.toJson(br);
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
            }
        }));
    }

    private void register() {
        this.registration_skeleton.createContext("/register", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                // parse request json
                RegisterRequest registerRequest = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    registerRequest = gson.fromJson(isr, RegisterRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }

                // Set the stubs for the newly-registered server and check for dup
                ServerInfo client_stub = new ServerInfo(registerRequest.storage_ip, registerRequest.client_port);
                this.client_stub = client_stub;
                if (registered_clients.contains(client_stub)){
                    this.sendExceptionReturn(exchange,"IllegalStateException", 400);
                    return;
                }else{
                    registered_clients.add(client_stub);
                }
                ServerInfo command_stub = new ServerInfo(registerRequest.storage_ip, registerRequest.command_port);
                this.command_stub = command_stub;
                if (registered_commands.contains(this.command_stub)){
                    this.sendExceptionReturn(exchange, "IllegalStateException", 400);
                    return;
                }else{
                    registered_commands.add(this.command_stub);
                }
                StorageInfo curr_storage = new StorageInfo(registerRequest.storage_ip, registerRequest.client_port,
                        registerRequest.command_port);
                if (!registered_storages.contains(curr_storage))
                    registered_storages.add(curr_storage);

                // prepare for the to-be deletedFiles
                ArrayList<String> to_be_deleted_files = new ArrayList<String>();
                // 遍历json request里面的files们然后加到list里面
                for (String file : registerRequest.files) {
                    if (file.equals("") || file.charAt(0) != '/' || file.contains(":")){
                        this.sendExceptionReturn(exchange,"IllegalStateException", 400);
                        return;
                    }
                    if (file.equals("/")) continue;
                    Address filePath = new Address(file);
                    if (filePath.exist()) to_be_deleted_files.add(filePath.toString());
                    else{
                        filePath.add(curr_storage);
                        // 为什么不能在这里 filePath.set_server_info是因为
                        // 你在add的过程中new了 所以这里不能直接set
                        // 虽然candi和加到tree里面最终的那个是一样的path
                        // 但是最终的那个是在扫children的时候new出来的所以这俩是不一样的obj！
                    }
                }
                FilesReturn filesReturn = new FilesReturn(to_be_deleted_files.toArray(new String[0]));
                respText = gson.toJson(filesReturn);
                returnCode = 200;
            }
            else {
                respText = "The REST method should be POST for <register>!\n";
                returnCode = 400;
            }
            this.generateResponseAndClose(exchange, respText, returnCode);
        }));
    }

    private void lock() {
        this.service_skeleton.createContext("/lock", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                LockRequest lr = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    lr = gson.fromJson(isr, LockRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                boolean exclusive = lr.exclusive;
                String path = lr.path;
                Address candi = new Address(path);
                if (path.equals("")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                if (!candi.exist()){
                    this.sendExceptionReturn(exchange, "FileNotFoundException", 400);
                    return;
                }
                try{
                    if (path.equals("/") && exclusive){
                        address_lock_table.get(Address.start).lockExclusive();
                    }else{
                        address_lock_table.get(Address.start).lockShared();
                    }
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
                Address.lock_along_the_path(path, exclusive, this.address_lock_table, registered_storages);
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
            }
        }));
    }

    private void unlock() {
        this.service_skeleton.createContext("/unlock", (exchange -> {
            String respText = "";
            int returnCode = 200;
            if ("POST".equals(exchange.getRequestMethod())) {
                LockRequest lr = null;
                ExceptionReturn er = null;
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                    lr = gson.fromJson(isr, LockRequest.class);
                } catch (Exception e) {
                    this.sendNotPostRequestReturn(exchange, respText, returnCode);
                    return;
                }
                boolean exclusive = lr.exclusive;
                String path = lr.path;
                Address candi = new Address(path);
                if (path.equals("")){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                if (!candi.exist()){
                    this.sendExceptionReturn(exchange, "IllegalArgumentException", 400);
                    return;
                }
                if (path.equals("/") && exclusive){
                    address_lock_table.get(Address.start).unlockExclusive();
                }else{
                    address_lock_table.get(Address.start).unlockShared();
                }
                Address.unlock_previous(path, exclusive, this.address_lock_table);
                this.generateResponseAndClose(exchange, respText, returnCode);
                return;
            }
        }));
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
    private void generateResponseAndClose(HttpExchange exchange, String respText, int returnCode) throws IOException {
        exchange.sendResponseHeaders(returnCode, respText.getBytes().length);
        OutputStream output = exchange.getResponseBody();
        output.write(respText.getBytes());
        output.flush();
        exchange.close();
    }

    public static void main(String[] args) throws FileNotFoundException {
        if (args.length != 2){
            System.out.println("Proper Usage is: java naming.NamingServer port port");
            System.exit(0);
        }
        SERVICE_PORT = Integer.parseInt(args[0]);
        REGISTRATION_PORT = Integer.parseInt(args[1]);
        PrintStream debug_file = new PrintStream(new FileOutputStream("debug_storage.txt", true));
        System.setOut(debug_file);
        try{
            NamingServer ns = new NamingServer();
            ns.start();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}
