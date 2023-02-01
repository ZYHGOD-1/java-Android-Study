package proxy;

public class RealProxy  implements IProxy{
    @Override
    public void doSomething() {
        System.out.println("do real something");
    }
}
