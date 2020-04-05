package naming;

/** This class stores information for each storage server*/
public class StorageInfo {
    public String server_ip;
    public int client_port;
    public int command_port;

    public StorageInfo(String server_ip, int client_port, int command_port) {
        this.server_ip = server_ip;
        this.client_port = client_port;
        this.command_port = command_port;
    }

    @Override
    public String toString() {
        return "ServerInfo: " + "server_ip = <" + server_ip + "> client_port = <" +
                client_port + ">" + "command_port = <" + command_port + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof StorageInfo)) return false;
        StorageInfo serverInfo = (StorageInfo) obj;
        return this.server_ip.equals(serverInfo.server_ip) && this.client_port== serverInfo.client_port &&
                this.command_port == serverInfo.command_port;
    }

    @Override
    public int hashCode() {
        return server_ip.hashCode() * 31 + client_port;
    }
}
