## LifeCycle详解
在日常的业务开发中，通常会针对Activity的不同生命周期做一些逻辑处理。随着需求的增多，这一部分代码会变得
非常臃肿。Google官方推出的JetPack框架中，通过观察者模式进行解耦。将具体化的业务逻辑，通过观察者的方式
独立出来。接下来将会对其原理进行详细解释
### Observer添加过程
首先我们可以看一下Observer的添加，以及注解是如何转化到实际代码的。</br>
在ComponentActivity中，LifeCycleOwner的最终实现类是LifecycleRegistry：
```java
private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);
```
LifecycleRegistry在添加observer时，其逻辑：
```java
   public void addObserver(@NonNull LifecycleObserver observer) {
        //确认是不是必须在主线程
        enforceMainThreadIfNeeded("addObserver");
        State initialState = mState == DESTROYED ? DESTROYED : INITIALIZED;
        //构建ObserverWIthState
        ObserverWithState statefulObserver = new ObserverWithState(observer, initialState);
        ObserverWithState previous = mObserverMap.putIfAbsent(observer, statefulObserver);

        if (previous != null) {
        return;
        }
        LifecycleOwner lifecycleOwner = mLifecycleOwner.get();
        if (lifecycleOwner == null) {
        // it is null we should be destroyed. Fallback quickly
        return;
        }

        boolean isReentrance = mAddingObserverCounter != 0 || mHandlingEvent;
        State targetState = calculateTargetState(observer);
        mAddingObserverCounter++;
        while ((statefulObserver.mState.compareTo(targetState) < 0
        && mObserverMap.contains(observer))) {
        pushParentState(statefulObserver.mState);
final Event event = Event.upFrom(statefulObserver.mState);
        if (event == null) {
        throw new IllegalStateException("no event up from " + statefulObserver.mState);
        }
        statefulObserver.dispatchEvent(lifecycleOwner, event);
        popParentState();
        // mState / subling may have been changed recalculate
        targetState = calculateTargetState(observer);
        }

        if (!isReentrance) {
        // we do sync only on the top level.
        sync();
        }
        mAddingObserverCounter--;
        }
```
在构建了ObserverWithState之后，就进行一些状态的分发。这一部分我们核心关注点在ObserverWithState的构建上：
```java
        ObserverWithState(LifecycleObserver observer, State initialState) {
        mLifecycleObserver = Lifecycling.lifecycleEventObserver(observer);
        mState = initialState;
        }
```
核心在Lifecyclint.lifecycleEventObserver(observer)：
```java
   static LifecycleEventObserver lifecycleEventObserver(Object object) {
        boolean isLifecycleEventObserver = object instanceof LifecycleEventObserver;
        boolean isFullLifecycleObserver = object instanceof FullLifecycleObserver;
        if (isLifecycleEventObserver && isFullLifecycleObserver) {
        return new FullLifecycleObserverAdapter((FullLifecycleObserver) object,
        (LifecycleEventObserver) object);
        }
        if (isFullLifecycleObserver) {
        return new FullLifecycleObserverAdapter((FullLifecycleObserver) object, null);
        }

        if (isLifecycleEventObserver) {
        return (LifecycleEventObserver) object;
        }

final Class<?> klass = object.getClass();
        int type = getObserverConstructorType(klass);
        if (type == GENERATED_CALLBACK) {
        List<Constructor<? extends GeneratedAdapter>> constructors =
        sClassToAdapters.get(klass);
        if (constructors.size() == 1) {
        GeneratedAdapter generatedAdapter = createGeneratedAdapter(
        constructors.get(0), object);
        return new SingleGeneratedAdapterObserver(generatedAdapter);
        }
        GeneratedAdapter[] adapters = new GeneratedAdapter[constructors.size()];
        for (int i = 0; i < constructors.size(); i++) {
        adapters[i] = createGeneratedAdapter(constructors.get(i), object);
        }
        return new CompositeGeneratedAdaptersObserver(adapters);
        }
        return new ReflectiveGenericLifecycleObserver(object);
        }
```
这里是针对我们的Observer进行适配，全部转换成了LifecycleEventObserver</br>
LifecycleEventObserver的构造分以下几种情况；
1. observer是LifecycleEventObserver
2. observer是FullLifeCycleObserver
3. 引入了lifecycle的注解处理器lifecycle-compiler，通过注解生成了类
4. 反射构造

以上这四种情况，最后都会转成LifecycleEventObserver。这里实际上就是使用了适配器模式，通过不同的适配器，将我们定义的observer转成统一的接口</br>
#### 1. observer是LifecycleEventObserver
如果observer instance of LifecycleEventObserver，就直接返回：
```java
        if (isLifecycleEventObserver) {
        return (LifecycleEventObserver) object;
        }
```
#### 2. observer是FullLifeCycleObserver
```java
        if (isFullLifecycleObserver) {
        return new FullLifecycleObserverAdapter((FullLifecycleObserver) object, null);
        }
```
这里通过FullLifecycleObserverAdapter这个适配器进行了转换，将接口转换成了LifecycleEventObserver,我们先看FullLifecycleObserver这个接口和LifeCycleEventObserver
```java
interface FullLifecycleObserver extends LifecycleObserver {

    void onCreate(LifecycleOwner owner);

    void onStart(LifecycleOwner owner);

    void onResume(LifecycleOwner owner);

    void onPause(LifecycleOwner owner);

    void onStop(LifecycleOwner owner);

    void onDestroy(LifecycleOwner owner);
}

public interface LifecycleEventObserver extends LifecycleObserver {
    void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event);
}
```
适配器：
```java
class FullLifecycleObserverAdapter implements LifecycleEventObserver {

    private final FullLifecycleObserver mFullLifecycleObserver;
    private final LifecycleEventObserver mLifecycleEventObserver;

    FullLifecycleObserverAdapter(FullLifecycleObserver fullLifecycleObserver,
                                 LifecycleEventObserver lifecycleEventObserver) {
        mFullLifecycleObserver = fullLifecycleObserver;
        mLifecycleEventObserver = lifecycleEventObserver;
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        switch (event) {
            case ON_CREATE:
                mFullLifecycleObserver.onCreate(source);
                break;
            case ON_START:
                mFullLifecycleObserver.onStart(source);
                break;
            case ON_RESUME:
                mFullLifecycleObserver.onResume(source);
                break;
            case ON_PAUSE:
                mFullLifecycleObserver.onPause(source);
                break;
            case ON_STOP:
                mFullLifecycleObserver.onStop(source);
                break;
            case ON_DESTROY:
                mFullLifecycleObserver.onDestroy(source);
                break;
            case ON_ANY:
                throw new IllegalArgumentException("ON_ANY must not been send by anybody");
        }
        if (mLifecycleEventObserver != null) {
            mLifecycleEventObserver.onStateChanged(source, event);
        }
    }
}
```
可以看到，这里实际上就是封装了一层，在onStateChange里面进行了转化与状态的分发。最终返回一个LifecycleEventObserver
#### 3.注解生成器生成的类
首先我们需要在build.gradle中引入注解生成器：
```java
annotationProcessor "androidx.lifecycle:lifecycle-compiler:2.5.1"
```
然后我们的Observer在build就会生成相应的类，比如我们有一个observer:
```java
public class MyLifeCycleObserver implements LifecycleObserver {
    public static final String TAG = "MyLifeCycleObserver";

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    public void connect(){
        Log.d(TAG, "connect: ");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart(){
        Log.d(TAG, "onStart: ");
    }
}
```
在build后，可以在generate中查看；
```java
public class ILifecycleBase_LifecycleAdapter implements GeneratedAdapter {
    final ILifecycleBase mReceiver;

    ILifecycleBase_LifecycleAdapter(ILifecycleBase receiver) {
        this.mReceiver = receiver;
    }

    @Override
    public void callMethods(LifecycleOwner owner, Lifecycle.Event event, boolean onAny,
                            MethodCallsLogger logger) {
        boolean hasLogger = logger != null;
        if (onAny) {
            if (!hasLogger || logger.approveCall("onAny", 4)) {
                mReceiver.onAny(owner,event);
            }
            return;
        }
        if (event == Lifecycle.Event.ON_CREATE) {
            if (!hasLogger || logger.approveCall("onCreate", 1)) {
                mReceiver.onCreate();
            }
            return;
        }
        if (event == Lifecycle.Event.ON_DESTROY) {
            if (!hasLogger || logger.approveCall("onDestroy", 1)) {
                mReceiver.onDestroy();
            }
            return;
        }
        if (event == Lifecycle.Event.ON_START) {
            if (!hasLogger || logger.approveCall("onStart", 1)) {
                mReceiver.onStart();
            }
            return;
        }
        if (event == Lifecycle.Event.ON_STOP) {
            if (!hasLogger || logger.approveCall("onStop", 1)) {
                mReceiver.onStop();
            }
            return;
        }
        if (event == Lifecycle.Event.ON_RESUME) {
            if (!hasLogger || logger.approveCall("onResume", 1)) {
                mReceiver.onResume();
            }
            return;
        }
        if (event == Lifecycle.Event.ON_PAUSE) {
            if (!hasLogger || logger.approveCall("onPause", 1)) {
                mReceiver.onPause();
            }
            return;
        }
    }
}
```
这里会自动生成一个GeneratedAdapter的接口，这个接口有个callMethods方法，实际上就是做一些转发
</br>
回到源码中的处理；
```java
int type = getObserverConstructorType(klass);
```
点进去看：
```java
    private static int getObserverConstructorType(Class<?> klass) {
        Integer callbackCache = sCallbackCache.get(klass);
        if (callbackCache != null) {
        return callbackCache;
        }
        int type = resolveObserverCallbackType(klass);
        sCallbackCache.put(klass, type);
        return type;
        }
```
这里通过resolveobserverCallbackType(klass)得到type，然后放到缓存里面：
```java
    private static int resolveObserverCallbackType(Class<?> klass) {
        // anonymous class bug:35073837
        if (klass.getCanonicalName() == null) {
        return REFLECTIVE_CALLBACK;
        }

        Constructor<? extends GeneratedAdapter> constructor = generatedConstructor(klass);
        if (constructor != null) {
        sClassToAdapters.put(klass, Collections
        .<Constructor<? extends GeneratedAdapter>>singletonList(constructor));
        return GENERATED_CALLBACK;
        }

@SuppressWarnings("deprecation")
        boolean hasLifecycleMethods = ClassesInfoCache.sInstance.hasLifecycleMethods(klass);
                if (hasLifecycleMethods) {
                return REFLECTIVE_CALLBACK;
                }

                Class<?> superclass = klass.getSuperclass();
        List<Constructor<? extends GeneratedAdapter>> adapterConstructors = null;
        if (isLifecycleParent(superclass)) {
        if (getObserverConstructorType(superclass) == REFLECTIVE_CALLBACK) {
        return REFLECTIVE_CALLBACK;
        }
        adapterConstructors = new ArrayList<>(sClassToAdapters.get(superclass));
        }

        for (Class<?> intrface : klass.getInterfaces()) {
        if (!isLifecycleParent(intrface)) {
        continue;
        }
        if (getObserverConstructorType(intrface) == REFLECTIVE_CALLBACK) {
        return REFLECTIVE_CALLBACK;
        }
        if (adapterConstructors == null) {
        adapterConstructors = new ArrayList<>();
        }
        adapterConstructors.addAll(sClassToAdapters.get(intrface));
        }
        if (adapterConstructors != null) {
        sClassToAdapters.put(klass, adapterConstructors);
        return GENERATED_CALLBACK;
        }

        return REFLECTIVE_CALLBACK;
        }
```
注解生成的有两种情况：
1. 本身就是由注解生成的类
2. 父类全是注解生成的

我们只需要看第一种情况即可，核心逻辑在generatedConstructor
```java
    private static Constructor<? extends GeneratedAdapter> generatedConstructor(Class<?> klass) {
        try {
        Package aPackage = klass.getPackage();
        String name = klass.getCanonicalName();
final String fullPackage = aPackage != null ? aPackage.getName() : "";
final String adapterName = getAdapterName(fullPackage.isEmpty() ? name :
        name.substring(fullPackage.length() + 1));

@SuppressWarnings("unchecked") final Class<? extends GeneratedAdapter> aClass =
        (Class<? extends GeneratedAdapter>) Class.forName(
        fullPackage.isEmpty() ? adapterName : fullPackage + "." + adapterName);
        Constructor<? extends GeneratedAdapter> constructor =
        aClass.getDeclaredConstructor(klass);
        if (!constructor.isAccessible()) {
        constructor.setAccessible(true);
        }
        return constructor;
        } catch (ClassNotFoundException e) {
        return null;
        } catch (NoSuchMethodException e) {
        // this should not happen
        throw new RuntimeException(e);
        }
        }
```
相信看到这里大家也明白了，就是生成新类的类名，通过class.forname加载，然后反射获取构造函数：
```java
    public static String getAdapterName(String className) {
        return className.replace(".", "_") + "_LifecycleAdapter";
        }
```
也就是说新的类名就是包名+类+_LifecycleAdapter，正好和之前对应上了
</br>
回到源码，知道是注解类型后生成适配器：
```java
       if (type == GENERATED_CALLBACK) {
        List<Constructor<? extends GeneratedAdapter>> constructors =
        sClassToAdapters.get(klass);
        if (constructors.size() == 1) {
        GeneratedAdapter generatedAdapter = createGeneratedAdapter(
        constructors.get(0), object);
        return new SingleGeneratedAdapterObserver(generatedAdapter);
        }
        GeneratedAdapter[] adapters = new GeneratedAdapter[constructors.size()];
        for (int i = 0; i < constructors.size(); i++) {
        adapters[i] = createGeneratedAdapter(constructors.get(i), object);
        }
        return new CompositeGeneratedAdaptersObserver(adapters);
        }
```
这里就只看SingleGeneratedAdapterObserver，首先会根据构造函数创建适配器,也就是我们的生成的_LifeCycleAdapter：
```java
    private static GeneratedAdapter createGeneratedAdapter(
        Constructor<? extends GeneratedAdapter> constructor, Object object) {
        //noinspection TryWithIdenticalCatches
        try {
        return constructor.newInstance(object);
        } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
        } catch (InstantiationException e) {
        throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
        throw new RuntimeException(e);
        }
        }
```
接下来看SingleGeneratedAdapterObserver:
```java
class SingleGeneratedAdapterObserver implements LifecycleEventObserver {

    private final GeneratedAdapter mGeneratedAdapter;

    SingleGeneratedAdapterObserver(GeneratedAdapter generatedAdapter) {
        mGeneratedAdapter = generatedAdapter;
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        mGeneratedAdapter.callMethods(source, event, false, null);
        mGeneratedAdapter.callMethods(source, event, true, null);
    }
}
```
这里就很清晰啦，转换成了我们要的LifecycleEventObserver。内部就是执行了callMethods方法
#### 4.反射转换的类
当不使用注解处理器时，就会通过反射去进行转换：
```java
return new ReflectiveGenericLifecycleObserver(object);
```
但是其实还有一部分逻辑在上文的resolveObserverCallbackType里面：
```java
        boolean hasLifecycleMethods = ClassesInfoCache.sInstance.hasLifecycleMethods(klass);
        if (hasLifecycleMethods) {
        return REFLECTIVE_CALLBACK;
        }
```
hasLifeCycleMethods:
```java
    boolean hasLifecycleMethods(Class<?> klass) {
        Boolean hasLifecycleMethods = mHasLifecycleMethods.get(klass);
        if (hasLifecycleMethods != null) {
        return hasLifecycleMethods;
        }

        Method[] methods = getDeclaredMethods(klass);
        for (Method method : methods) {
        OnLifecycleEvent annotation = method.getAnnotation(OnLifecycleEvent.class);
        if (annotation != null) {
        createInfo(klass, methods);
        return true;
        }
        }
        mHasLifecycleMethods.put(klass, false);
        return false;
        }
```
也就是通过反射拿方法的OnLifecycleEvent注解，如果有就调用createInfo:
```java
    private CallbackInfo createInfo(Class<?> klass, @Nullable Method[] declaredMethods) {
        Class<?> superclass = klass.getSuperclass();
        Map<MethodReference, Lifecycle.Event> handlerToEvent = new HashMap<>();
        if (superclass != null) {
        CallbackInfo superInfo = getInfo(superclass);
        if (superInfo != null) {
        handlerToEvent.putAll(superInfo.mHandlerToEvent);
        }
        }

        Class<?>[] interfaces = klass.getInterfaces();
        for (Class<?> intrfc : interfaces) {
        for (Map.Entry<MethodReference, Lifecycle.Event> entry : getInfo(
        intrfc).mHandlerToEvent.entrySet()) {
        verifyAndPutHandler(handlerToEvent, entry.getKey(), entry.getValue(), klass);
        }
        }

        Method[] methods = declaredMethods != null ? declaredMethods : getDeclaredMethods(klass);
        boolean hasLifecycleMethods = false;
        for (Method method : methods) {
        OnLifecycleEvent annotation = method.getAnnotation(OnLifecycleEvent.class);
        if (annotation == null) {
        continue;
        }
        hasLifecycleMethods = true;
        Class<?>[] params = method.getParameterTypes();
        int callType = CALL_TYPE_NO_ARG;
        if (params.length > 0) {
        callType = CALL_TYPE_PROVIDER;
        if (!params[0].isAssignableFrom(LifecycleOwner.class)) {
        throw new IllegalArgumentException(
        "invalid parameter type. Must be one and instanceof LifecycleOwner");
        }
        }
        Lifecycle.Event event = annotation.value();

        if (params.length > 1) {
        callType = CALL_TYPE_PROVIDER_WITH_EVENT;
        if (!params[1].isAssignableFrom(Lifecycle.Event.class)) {
        throw new IllegalArgumentException(
        "invalid parameter type. second arg must be an event");
        }
        if (event != Lifecycle.Event.ON_ANY) {
        throw new IllegalArgumentException(
        "Second arg is supported only for ON_ANY value");
        }
        }
        if (params.length > 2) {
        throw new IllegalArgumentException("cannot have more than 2 params");
        }
        MethodReference methodReference = new MethodReference(callType, method);
        verifyAndPutHandler(handlerToEvent, methodReference, event, klass);
        }
        CallbackInfo info = new CallbackInfo(handlerToEvent);
        mCallbackMap.put(klass, info);
        mHasLifecycleMethods.put(klass, hasLifecycleMethods);
        return info;
        }
```
我们只需要关注循环的核心逻辑，其他都是一些父类、缓存的判断。循环中遍历method，然后根据params个数执行一些判断。这里我们也可以发现，参数个数不能大于2，第一个参数只能是LifecycleOwner，第二个只能是给ON_ANY用的
</br>
然后生成了methodReference,这个也没什么，就是参数个数控制的类：
```java
        MethodReference(int callType, Method method) {
        mCallType = callType;
        mMethod = method;
        mMethod.setAccessible(true);
        }

        void invokeCallback(LifecycleOwner source, Lifecycle.Event event, Object target) {
        //noinspection TryWithIdenticalCatches
        try {
        switch (mCallType) {
        case CALL_TYPE_NO_ARG:
        mMethod.invoke(target);
        break;
        case CALL_TYPE_PROVIDER:
        mMethod.invoke(target, source);
        break;
        case CALL_TYPE_PROVIDER_WITH_EVENT:
        mMethod.invoke(target, source, event);
        break;
        }
        } catch (InvocationTargetException e) {
        throw new RuntimeException("Failed to call observer method", e.getCause());
        } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
        }
        }
```
然后调用verifyAndPutHandler：
```java
    private void verifyAndPutHandler(Map<MethodReference, Lifecycle.Event> handlers,
        MethodReference newHandler, Lifecycle.Event newEvent, Class<?> klass) {
        Lifecycle.Event event = handlers.get(newHandler);
        if (event != null && newEvent != event) {
        Method method = newHandler.mMethod;
        throw new IllegalArgumentException(
        "Method " + method.getName() + " in " + klass.getName()
        + " already declared with different @OnLifecycleEvent value: previous"
        + " value " + event + ", new value " + newEvent);
        }
        if (event == null) {
        handlers.put(newHandler, newEvent);
        }
        }
```
这里也可以看到，一个方法不能注册两个事件，因为存储的结构是一个hashmap,这里其实就是把<MethodReference,event>这个二元组放到hashmap里面
循环结束后：
```java
        CallbackInfo info = new CallbackInfo(handlerToEvent);
        mCallbackMap.put(klass, info);
        mHasLifecycleMethods.put(klass, hasLifecycleMethods);
```
通过保存的hashmap，构造callabckInfo，然后缓存。
那么回到ReflectiveGenericLifecycleObserver：
```java
class ReflectiveGenericLifecycleObserver implements LifecycleEventObserver {
    private final Object mWrapped;
    private final androidx.lifecycle.ClassesInfoCache.CallbackInfo mInfo;

    @SuppressWarnings("deprecation")
    ReflectiveGenericLifecycleObserver(Object wrapped) {
        mWrapped = wrapped;
        mInfo = ClassesInfoCache.sInstance.getInfo(mWrapped.getClass());
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Event event) {
        mInfo.invokeCallbacks(source, event, mWrapped);
    }
}
```
这里就很简单了，拿到生成的CallbackInfo，实际方法载体就是通过info.invokeCallback。
```java
        void invokeCallbacks(LifecycleOwner source, Lifecycle.Event event, Object target) {
        invokeMethodsForEvent(mEventToHandlers.get(event), source, event, target);
        invokeMethodsForEvent(mEventToHandlers.get(Lifecycle.Event.ON_ANY), source, event,
        target);
        }
```
这里就是通过我们之前存的hashmap,取出来调用</br>
至此，四种情况下的observer转换就讲解完了。接下来
```java
        while ((statefulObserver.mState.compareTo(targetState) < 0
        && mObserverMap.contains(observer))) {
        pushParentState(statefulObserver.mState);
final Event event = Event.upFrom(statefulObserver.mState);
        if (event == null) {
        throw new IllegalStateException("no event up from " + statefulObserver.mState);
        }
        statefulObserver.dispatchEvent(lifecycleOwner, event);
        popParentState();
        // mState / subling may have been changed recalculate
        targetState = calculateTargetState(observer);
        }
```
这里的逻辑非常简单，取最小值计算目标state和event,然后直接分发下去，调用dispatchEvent:
```java
        void dispatchEvent(LifecycleOwner owner, Event event) {
        State newState = event.getTargetState();
        mState = min(mState, newState);
        mLifecycleObserver.onStateChanged(owner, event);
        mState = newState;
        }
```
这里调用onStateChanged,就回到了我们上文所描述的。

### LifeCycle状态转换流程
#### Activity生命周期与state
上文所述的在addObserver的过程中，计算observer的目标状态：
```java
    private State calculateTargetState(LifecycleObserver observer) {
        Map.Entry<LifecycleObserver, ObserverWithState> previous = mObserverMap.ceil(observer);

        State siblingState = previous != null ? previous.getValue().mState : null;
        State parentState = !mParentStates.isEmpty() ? mParentStates.get(mParentStates.size() - 1)
                : null;
        return min(min(mState, siblingState), parentState);
    }
```
这里取了parentState 、observer自身状态、lifecycle状态的最小值。parentState是为了保证不变性：当有observer在生命周期回调中remove了自身，保证observer执行的顺序.
</br>
那么lifecycle状态是从何处得到的呢？我们来看ComponentActivity:
```java
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mSavedStateRegistryController.performRestore(savedInstanceState);
        mContextAwareHelper.dispatchOnContextAvailable(this);
        super.onCreate(savedInstanceState);
        ReportFragment.injectIfNeededIn(this);
        if (BuildCompat.isAtLeastT()) {
            mOnBackPressedDispatcher.setOnBackInvokedDispatcher(
                    Api33Impl.getOnBackInvokedDispatcher(this)
            );
        }
        if (mContentLayoutId != 0) {
            setContentView(mContentLayoutId);
        }
    }
```
别的不用看，主要在ReportFragment.InjectIfNeededIn(this):
```java
    public static void injectIfNeededIn(Activity activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            // On API 29+, we can register for the correct Lifecycle callbacks directly
            LifecycleCallbacks.registerIn(activity);
        }
       // ……
        }
    }
```
这里调用了LifecycleCallbacks.registerIn:
```java
        static void registerIn(Activity activity) {
            activity.registerActivityLifecycleCallbacks(new LifecycleCallbacks());
        }
```
这里就很清楚了，注册了一个Activity的生命周期回调，通过这个回调，去感知Activity的生命周期状态。我们来看一下这个回调；
```java
    static class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

        static void registerIn(Activity activity) {
            activity.registerActivityLifecycleCallbacks(new LifecycleCallbacks());
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity,
                @Nullable Bundle bundle) {
        }

        @Override
        public void onActivityPostCreated(@NonNull Activity activity,
                @Nullable Bundle savedInstanceState) {
            dispatch(activity, Lifecycle.Event.ON_CREATE);
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPostStarted(@NonNull Activity activity) {
            dispatch(activity, Lifecycle.Event.ON_START);
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPostResumed(@NonNull Activity activity) {
            dispatch(activity, Lifecycle.Event.ON_RESUME);
        }

        @Override
        public void onActivityPrePaused(@NonNull Activity activity) {
            dispatch(activity, Lifecycle.Event.ON_PAUSE);
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPreStopped(@NonNull Activity activity) {
            dispatch(activity, Lifecycle.Event.ON_STOP);
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity,
                @NonNull Bundle bundle) {
        }

        @Override
        public void onActivityPreDestroyed(@NonNull Activity activity) {
            dispatch(activity, Lifecycle.Event.ON_DESTROY);
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
```
dispatch方法：
```java
    static void dispatch(@NonNull Activity activity, @NonNull Lifecycle.Event event) {
    if (activity instanceof LifecycleOwner) {
            Lifecycle lifecycle = ((LifecycleOwner) activity).getLifecycle();
            if (lifecycle instanceof LifecycleRegistry) {
                ((LifecycleRegistry) lifecycle).handleLifecycleEvent(event);
            }
        }
    }
```
通过调用lifecycle也就是lifecycleRegistry的handleLifecycleEvent方法，实现lifecycle状态的更新。

#### 事件与状态流转
上一小节中看到，是通过一个Activity的生命周期回调去获得的状态改变。从那个接口可以得到，其执行时机和对应事件：
1. Activity on create之后，事件为ON_CREATE
2. Activity on start之后，事件为ON_START
3. Activity on resume之后，事件为ON_RESUME
4. Activity on pause之前，事件为ON_PAUSE
5. Activity on stop之前，事件为ON_STOP
6. Activity on Destroy之前，事件为ON_DESTROY

再来看LifeCycleRegistry的handleLifecycleEvent方法：
```java
    public void handleLifecycleEvent(@NonNull Lifecycle.Event event) {
        enforceMainThreadIfNeeded("handleLifecycleEvent");
        moveToState(event.getTargetState());
    }
```
这里主要是调用了moveState。通过event.getTargetState来确定状态：
```java
        public State getTargetState() {
            switch (this) {
                case ON_CREATE:
                case ON_STOP:
                    return State.CREATED;
                case ON_START:
                case ON_PAUSE:
                    return State.STARTED;
                case ON_RESUME:
                    return State.RESUMED;
                case ON_DESTROY:
                    return State.DESTROYED;
                case ON_ANY:
                    break;
            }
            throw new IllegalArgumentException(this + " has no target state");
        }
```
这里对应了官方的一张图：
<img src = "https://p1-jj.byteimg.com/tos-cn-i-t2oaga2asx/gold-user-assets/2020/3/9/170bd0db1a8cebc8~tplv-t2oaga2asx-zoom-in-crop-mark:4536:0:0:0.image"/>
表示的是事件对应的目标状态。拿到这个状态后，就开始了分发状态流程：
```java
    private void moveToState(State next) {
        if (mState == next) {
            return;
        }
        if (mState == INITIALIZED && next == DESTROYED) {
            throw new IllegalStateException("no event down from " + mState);
        }
        mState = next;
        if (mHandlingEvent || mAddingObserverCounter != 0) {
            mNewEventOccurred = true;
            // we will figure out what to do on upper level.
            return;
        }
        mHandlingEvent = true;
        sync();
        mHandlingEvent = false;
        if (mState == DESTROYED) {
            mObserverMap = new FastSafeIterableMap<>();
        }
    }
```
核心逻辑在sync内部：
```java
    private void sync() {
        LifecycleOwner lifecycleOwner = mLifecycleOwner.get();
        if (lifecycleOwner == null) {
            throw new IllegalStateException("LifecycleOwner of this LifecycleRegistry is already"
                    + "garbage collected. It is too late to change lifecycle state.");
        }
        while (!isSynced()) {
            mNewEventOccurred = false;
            // no need to check eldest for nullability, because isSynced does it for us.
            if (mState.compareTo(mObserverMap.eldest().getValue().mState) < 0) {
                backwardPass(lifecycleOwner);
            }
            Map.Entry<LifecycleObserver, ObserverWithState> newest = mObserverMap.newest();
            if (!mNewEventOccurred && newest != null
                    && mState.compareTo(newest.getValue().mState) > 0) {
                forwardPass(lifecycleOwner);
            }
        }
        mNewEventOccurred = false;
    }
```
isSync()会校验所有的observer是否和当前的状态一致。这里主要做了两件事情：
1. 将当前状态和最老的observer状态比较，如果小于，就让所有observer 负向流转（参考前文的状态图），和mstate一样
2. 如果最新的observer状态小于mstate，就正向流转，和mstate保持一致

我们先看backwardPass:
```java
    private void backwardPass(LifecycleOwner lifecycleOwner) {
        Iterator<Map.Entry<LifecycleObserver, ObserverWithState>> descendingIterator =
                mObserverMap.descendingIterator();
        while (descendingIterator.hasNext() && !mNewEventOccurred) {
            Map.Entry<LifecycleObserver, ObserverWithState> entry = descendingIterator.next();
            ObserverWithState observer = entry.getValue();
            while ((observer.mState.compareTo(mState) > 0 && !mNewEventOccurred
                    && mObserverMap.contains(entry.getKey()))) {
                Event event = Event.downFrom(observer.mState);
                if (event == null) {
                    throw new IllegalStateException("no event down from " + observer.mState);
                }
                pushParentState(event.getTargetState());
                observer.dispatchEvent(lifecycleOwner, event);
                popParentState();
            }
        }
    }
```
这里比较后发现如果状态大于mstate,就负向流转，使用downFrom获得事件。对应的，forwordPass通过upFrom获得事件。事件拿到后，就进行dispatch
```java
        public static Event upFrom(@NonNull State state) {
            switch (state) {
                case INITIALIZED:
                    return ON_CREATE;
                case CREATED:
                    return ON_START;
                case STARTED:
                    return ON_RESUME;
                default:
                    return null;
            }
        }

public static Event downFrom(@NonNull State state) {
        switch (state) {
        case CREATED:
        return ON_DESTROY;
        case STARTED:
        return ON_STOP;
        case RESUMED:
        return ON_PAUSE;
default:
        return null;
        }
        }
```
这里也可以看出，实际上获得的就是正向/负向流转对应的下一个事件。
</br>
至此，Lifecycle就分析完了。总结一下：在添加observer的时候，会通过适配器进行接口转换，lifecycle通过Activity生命周期回调来监听Activity的生命周期流程，获得目标状态后，会通过正向/负向流转先遍历各个observer，让其和自身状态同步，然后将新的状态进行分发
### 设计模式在LifeCycle中的应用（一） 观察者模式
### 设计模式在LifeCycle中的应用 （二） 适配器模式