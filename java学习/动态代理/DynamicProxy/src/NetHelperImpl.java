import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class NetHelperImpl {
    public static INetHelper getNetHelper(){
        INetHelper netHelper = (INetHelper) Proxy.newProxyInstance(INetHelper.class.getClassLoader(), new Class[]{INetHelper.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if(method.getName().equals("post")){
                    System.out.println("执行post方法");
                }else if(method.getName().equals("get")){
                    System.out.println("执行get方法");
                }
                return null;
            }
        });
        return  netHelper;
    }
}
