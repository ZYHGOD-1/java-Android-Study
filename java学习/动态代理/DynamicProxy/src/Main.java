public class Main {
    public static void main(String[] args) {
        System.setProperty("jdk.proxy.ProxyGenerator.saveGeneratedFiles","true");
           NetHelperImpl.getNetHelper().get();
           NetHelperImpl.getNetHelper().post();
    }
}