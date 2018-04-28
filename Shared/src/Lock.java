// combination better than inheritance here
class Lock {
    private WaitGroup wg = new WaitGroup();
    private boolean locked = false;

    boolean locked() {
        return locked;
    }

    void lock() {
        locked = true;
        wg.wait(1);
        locked = false;
    }

    void unlock() {
        wg.done();
    }
}
