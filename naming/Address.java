package naming;

import jsonhelper.CopyRequest;
import jsonhelper.PathRequest;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** This class sets up the main data structure that the naming server use to track the files and directories*/
public class Address {
    /** list of files it contains if self is directory */
    private List<Address> children;
    private String path;
    /** whether the curr file is directory or not*/
    private boolean isDir;
    // 如果只需要一个的话必须是static否则会重复initialize造成stackoverflow
    /** start as the root !*/
    protected static final Address start = new Address();
    /** info of the storage server that hosts this file*/
    private StorageInfo si;
    /** number of times that this file is being accessed*/
    private int accessNum;
    /** list of replica storage servers that have copies (not host) of this file*/
    private List<StorageInfo> replicaServers;
    /** gson obj for parsing json*/
    protected static final Gson gson = new Gson();

    /** initialize as a normal directory by default. Field subject to change if it is a file */
    public Address(String path){
        this.path = path;
        this.children = new ArrayList<>();
        this.isDir = true; // true by default
        this.si = null;
        this.accessNum = 0;
        this.replicaServers = new ArrayList<>();
    }
    /** initialize the root dir */
    public Address(){
        this.path = "";
        this.children = new ArrayList<>();
        this.isDir = true;
        this.si = null;
    }

    /** set self to be file*/
    public void set_isFile(){
        this.isDir = false;
    }
    /** get the storage server that host this file*/
    public StorageInfo get_serverInfo(){
        Address curr = start;
        String[] parts = eliminate_spaces(this.path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            if (!curr.children.contains(candi)){
                return null;
            }else{
                for (Address child : curr.children){
                    if (child.equals(candi)){
                        curr = child;
                        break;
                    }
                }
            }
        }
        return curr.si;
    }

    public List<StorageInfo> get_replica_servers(){
        return this.replicaServers;
    }

    public void add_new_replica_server(StorageInfo si){
        this.replicaServers.add(si);
    }

    public void remove_one_replica_server(StorageInfo si){
        this.replicaServers.remove(si);
    }

    public boolean get_is_dir(){
        return this.isDir;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Address)) {
            return false;
        }
        Address other = (Address) o;
        return other.path.equals(path);
    }

    @Override
    public String toString() {
        return path;
    }

    public static String[] eliminate_spaces(String path){
        List<String> result = new ArrayList<>();
        String[] parts = path.split("/");
        for (String each : parts){
            if (!each.equals("")) result.add(each);
        }
        return result.toArray(new String[0]);
    }

    /** 如果有一个路径不匹配就返回false 必须得一模一样才行 */
    public boolean exist(){
        Address curr = start;
        String[] parts = eliminate_spaces(this.path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            if (!curr.children.contains(candi)){
                return false;
            }else{
                for (Address child : curr.children){
                    if (child.equals(candi)){
                        curr = child;
                        break;
                    }
                }
            }
        }
        return true;
    }

    /** adds the file from the scratch upon registration*/
    public void add(StorageInfo si){
        Address curr = start;
        String[] parts = eliminate_spaces(this.path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            if (!curr.children.contains(candi)){
                if (i == parts.length - 1){
                    candi.set_isFile();
                }
                curr.children.add(candi);
                curr = candi;
                curr.si = si;   //  刚加的 不一定对！！！！
            }else{
                for (Address each : curr.children){
                    if (candi.equals(each)){
                        curr = each;
                        curr.si = si;   // 刚加的 不一定对！！！！
                        break;
                    }
                }
            }
        }
        curr.si = si;
    }
    /** delete the file totally from the data structure including copies */
    public void delete(){
        Address curr = start;
        String[] parts = eliminate_spaces(this.path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            for (Address each : curr.children){
                if (candi.equals(each)) {
                    if (i != parts.length - 1) {
                        curr = each;
                        break;
                    } else {
                        if (!each.is_Directory()){
                            deleteReplicas(each);
                            PathRequest pr = new PathRequest(each.toString());
                            try{
                                HttpResponse<String> response = getResponse(each.get_serverInfo().server_ip,
                                        candi.get_serverInfo().command_port, "/storage_delete", pr);
                            }catch(IOException e){
                                e.printStackTrace();
                            }catch(InterruptedException e){
                                e.printStackTrace();
                            }
                            curr.children.remove(each);
                            return;
                        }else{
                            for (Address child : each.children){
                                if (!child.is_Directory()){
                                    deleteReplicas(child);
                                    PathRequest pr = new PathRequest(each.toString());
                                    try{
                                        HttpResponse<String> response = getResponse(child.get_serverInfo().server_ip,
                                                child.get_serverInfo().command_port, "/storage_delete", pr);
                                    }catch(IOException e){
                                        e.printStackTrace();
                                    }catch(InterruptedException e){
                                        e.printStackTrace();
                                    }
                                    deleteReplicas(each);
                                    try{
                                        HttpResponse<String> response = getResponse(each.get_serverInfo().server_ip,
                                                each.get_serverInfo().command_port, "/storage_delete", pr);
                                    }catch(IOException e){
                                        e.printStackTrace();
                                    }catch(InterruptedException e){
                                        e.printStackTrace();
                                    }
                                    curr.children.remove(each);
                                    return;
                                }
                            }

                        }

                    }
                }
            }
        }
    }
    /** return true if directory created successfullly, false otherwise */
    public boolean create_directory(){
        Address curr = start;
        String[] parts = eliminate_spaces(this.path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            if (!curr.children.contains(candi)){
                if (i != parts.length - 1){
                    return false;
                }
                curr.children.add(candi);
                curr = candi;
            }else{
                for (Address each : curr.children){
                    if (candi.equals(each)){
                        // parent dir is actually file, not good
                        if (!each.get_is_dir()) return false;
                        curr = each;
                        break;
                    }
                }
            }
        }

        return true;
    }
    /** return true if file created successfullly, false otherwise */
    public boolean create_file(){
        Address curr = start;
        String[] parts = eliminate_spaces(this.path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            if (!curr.children.contains(candi)){
                // parent dir not exist; x create file
                if (i != parts.length - 1){
                    return false;
                }
                candi.set_isFile();
                curr.children.add(candi);
                curr = candi;
            }else{
                for (Address each : curr.children){
                    if (candi.equals(each)){
                        // parent dic is actually file; not good
                        if (!each.get_is_dir()) return false;
                        curr = each;
                        break;
                    }
                }
            }
        }
        return true;
    }

    public boolean is_Directory(){
        Address curr = start;
        String[] parts = eliminate_spaces(this.path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            for (Address each : curr.children) {
                if (candi.equals(each)) {
                    curr = each;
                    break;
                }
            }
        }
        return curr.get_is_dir();
    }

    public List<String> list_under_dir(){
        Address curr = start;
        List<String> result = new ArrayList<>();
        // if not the root
        if (!this.path.equals("/")){
            String[] parts = eliminate_spaces(this.path);
            for (int i = 0; i < parts.length; i++){
                Address candi = new Address(curr.path + "/" + parts[i]);
                for (Address each : curr.children){
                    if (candi.equals(each)) {
                        curr = each;
                        break;
                    }
                }
            }
        }
        // if it IS root
        for (Address each : curr.children){
            String[] parts2 = eliminate_spaces(each.path);
            result.add(parts2[parts2.length - 1]);
        }
        return result;
    }

    /** increase the access time of the file and check if it is larger than 20*/
    public boolean incAccessTime(int multiple) {
        if (++accessNum > multiple) {
            accessNum = 0;
            return true;
        }
        return false;
    }

    public void resetAccess(){
        accessNum = 0;
    }

    /** list all the files like literally ALL the files */
    public static List<Address> list(){
        List<Address> result = new ArrayList<>();
        Address curr = start;
        dfs(result, curr);
        return result;
    }

    public static void dfs(List<Address> result, Address curr){
        for (Address child : curr.children){
            result.add(child);
            dfs(result, child);
        }
    }
    /** lock according to whether the candidate is file or directory */
    public static void lock_along_the_path(String path, boolean exclusive, ConcurrentHashMap<Address, RWLock> map,
                                           List<StorageInfo> registered_servers){
        Address curr = start;
        String[] parts = eliminate_spaces(path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            if (curr.children.contains(candi)){
                for (Address each : curr.children){
                    if (candi.equals(each)){
                        curr = each;
                        if (!map.containsKey(curr)) map.put(curr, new RWLock());
                        break;
                    }
                }
                try{
                    // if file and it is write request, lock exclusive
                    if (exclusive && i == parts.length - 1){
                        map.get(curr).lockExclusive();
                            curr.resetAccess();
                            deleteReplicas(curr);
                    }else{
                        map.get(curr).lockShared();
                        // if file and being accessed larger than 20 times
                        if (!curr.is_Directory() && curr.incAccessTime(20)){
                            replicate(curr, registered_servers);
                        }
                    }
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
            }else{
                unlock_previous(curr.path + "/" + parts[i], false, map);
            }

        }

    }
    /** unlock everything that are ancestors of the file besides the file itself */
    public static void unlock_previous(String path, boolean exclusive, ConcurrentHashMap<Address, RWLock> map){
        Address curr = start;
        String[] parts = eliminate_spaces(path);
        for (int i = 0; i < parts.length; i++){
            Address candi = new Address(curr.path + "/" + parts[i]);
            if (curr.children.contains(candi)){
                for (Address each : curr.children){
                    if (candi.equals(each)){
                        curr = each;
                        break;
                    }
                }
                if (exclusive && i == parts.length - 1){
                    map.get(curr).unlockExclusive();
                }else{
                    map.get(curr).unlockShared();
                }
            }
        }
    }
    /** Let every storage server except the server hosting the file has the copy of the file */
    private static void replicate(Address candi, List<StorageInfo> registered_servers){
        // 因为你身为storage server你不能用自己的command stub给自己的client stub发lol
        for (StorageInfo si : registered_servers){
            if (!candi.get_replica_servers().contains(si) && !candi.get_serverInfo().equals(si)){
                candi.add_new_replica_server(si);
                CopyRequest cr = new CopyRequest(candi.toString(), candi.get_serverInfo().server_ip,
                        candi.get_serverInfo().client_port);
                try{
                    HttpResponse<String> response = getResponse(si.server_ip, si.command_port, "/storage_copy", cr);
                }catch(IOException e){
                    System.out.println(e.getMessage());
                }catch(InterruptedException e){
                    System.out.println(e.getMessage());
                }
            }
        }
    }
    /** delete every copy that other storage server is hosting*/
    private static void deleteReplicas(Address candi){
        for (int i = 0; i < candi.get_replica_servers().size(); i++){
            PathRequest pr = new PathRequest(candi.toString());
            try{
                HttpResponse<String> response = getResponse(candi.get_replica_servers().get(i).server_ip,
                        candi.get_replica_servers().get(i).command_port, "/storage_delete", pr);
                candi.remove_one_replica_server(candi.get_replica_servers().get(i));
            }catch(IOException e){
                e.printStackTrace();
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    private static HttpResponse<String> getResponse(String ip, int port, String api, Object obj) throws IOException,
            InterruptedException{
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + ":" + port + api))
                .setHeader("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(obj)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

}
