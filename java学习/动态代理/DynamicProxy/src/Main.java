import proxy.ProxySubject;

public class Main {
    public static void main(String[] args) {
        System.setProperty("jdk.proxy.ProxyGenerator.saveGeneratedFiles","true");
           NetHelperImpl.getNetHelper().get();
           NetHelperImpl.getNetHelper().post();
        ProxySubject proxySubject = new ProxySubject();
        proxySubject.doSomething();
    }
}