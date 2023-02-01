package proxy;

public class ProxySubject implements IProxy {
    IProxy proxy = new RealProxy();

    public ProxySubject(IProxy proxy) {
        this.proxy = proxy;
    }

    public ProxySubject() {
    }

    @Override
    public void doSomething() {
        proxy.doSomething();
    }
}
