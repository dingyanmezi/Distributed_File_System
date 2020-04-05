package naming;

public class RWLock {
    private int readReqeust;
    private int writeRequest;
    private boolean isWriting;

    public RWLock(){
        this.readReqeust = 0;
        this.writeRequest = 0;
        this.isWriting = false;
    }

    public synchronized void lockShared() throws InterruptedException
    {
        // block when exclusive access requests exist (to ensure FCFS) or writing is ongoing
        // Remember the example "A B currently shared access, C exclusive access request, D shared access request"
        // then D should wait after C unlock
        while (writeRequest > 0 || isWriting)
            wait();
        // increment the number of times for read
        readReqeust++;
    }

    public synchronized void lockExclusive() throws InterruptedException
    {
        // increment write request
        // Again this is for the "A B C D" example situation mentioned previously
        // make sure D wait AFTER C !
        writeRequest++;
        // cannot be simultaneously locked by users requesting exclusive access if shared accesses ongoing.
        // or when there is exclusive access (writing) going on
        while (readReqeust > 0 || isWriting)
            wait();
        writeRequest--;
        isWriting = true;
    }

    public synchronized void unlockShared()
    {
        readReqeust--;
        // wake up everybody
        notifyAll();
    }

    public synchronized void unlockExclusive()
    {
        isWriting = false;
        notifyAll();
    }
}