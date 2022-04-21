# java nio demo
[维基百科 - Java NIO](https://zh.wikipedia.org/wiki/Java_NIO)

以多种方式实现非阻塞demo（单线程100并发），实现的方式有：
java nio、java nio2、jdk11 HttpClient、纯Netty、Spring WebFlux WebClient、Vert.x HttpClient、kotlin coroutine + ktor、kotlin coroutine + cio、kotlin coroutine + webflux、kotlin coroutine + nio2、loom + hutool bio；

bio对照组：java socket io、hutool HttpUtil、spring cloud feign；

## 效果
比如说可以自己实现一个，单线程100并发，访问响应时长5s的rest api的demo，5s是为了让结果更明显；
验证：用jprofile看下线程数确保只有一个线程，11s内，能访问这个api 200次

要求：
- 由于各种框架产生的调度线程，我们不能保证线程全部是自己产生的，用jprofile看下，线程总数量小于10就说明没问题；
  - 错误示例：logback-1、logback-2 ...
- 如果线程总数量大于99，说明用的是阻塞式，没起到非阻塞的作用；
  - 错误示例：如果java nio2的没设置AsynchronousChannelGroup，那么每open一个socket都会新开一个线程；
- 如果总时长大于15s，或者总的成功响应次数小于200次，说明并行没达到要求；
  - 错误示例：我这里试验vertx出现过，默认的并行数上限是5，需要配置；
- 如果总时长小于10s，说明服务器单词请求响应时长没有5s，或者请求次数不到200，或者并行数大于100了
  - 错误示例：我这里试验webflux出现过，操作符效果和预期不一致，导致200并发、单并发1次请求的效果

## demo代码
这里的示例为了使单独的类都是有效代码，阅读代码更直观，跨类不做逻辑复用、不做方法提取。

### 服务端：
com.example.javaniodemo.JavaNioDemoApplication
直接用webflux+coroutine，这里充当公用服务，不做任何修改。
并且这个组合原生支持上述的条件。
因为写服务端代码需要管理连接状态，难度比客户端高了50%，所以只通过客户端来实现、验证、对比。
也可以用spring boot + tomcat + Thread.sleep(5000)实现下服务端，jprofile看下效果。

### 客户端并发实现代码
所在包：test下的[com.example.javaniodemo.demo](./src/test/kotlin/com/example/javaniodemo/demo)

- 纯java
  - 纯nio [JavaNioDemo](src/test/kotlin/com/example/javaniodemo/demo/JavaNioDemo.java)
  - 纯nio2 [JavaNio2Demo](src/test/kotlin/com/example/javaniodemo/demo/JavaNio2Demo.java)
    - 这里踩了下坑，网上大部分教程读作nio，写成bio，根本就没有非阻塞效果、一个io严格对应1+个线程，包括
      - [oracle官方教程](https://docs.oracle.com/en/java/javase/17/core/non-blocking-time-server-nio-example.html)
      - [baeldung大佬的教程](https://www.baeldung.com/java-nio2-async-socket-channel)
      - 各路csdn文章
    - 这里的demo代码才是nio的正确写法
  - jdk11 http [Jdk11HttpClientNioDemo](src/test/kotlin/com/example/javaniodemo/demo/Jdk11HttpClientNioDemo.java)
- java语法下的外部库回调式、外置事件循环io框架（函数式非阻塞）
  - 纯netty [NettyNioDemo](src/test/kotlin/com/example/javaniodemo/demo/NettyNioDemo.java)
  - spring mono [SpringWebClientNioDemo](src/test/kotlin/com/example/javaniodemo/demo/SpringWebClientNioDemo.java)
  - Vert.x [VertxHttpClientNioDemo](src/test/kotlin/com/example/javaniodemo/demo/VertxHttpClientNioDemo.java)
- kotlin协程
  - mono coroutine [CoroutineMonoNioDemo](src/test/kotlin/com/example/javaniodemo/demo/CoroutineMonoNioDemo.kt)
  - ktor [CoroutineKtorNioDemo](src/test/kotlin/com/example/javaniodemo/demo/CoroutineKtorNioDemo.kt)
  - 纯CIO [CoroutineCioNioDemo](src/test/kotlin/com/example/javaniodemo/demo/CoroutineCioNioDemo.kt)
    - 可以对比下java bio的写法，几乎一模一样；也就是说kt下可以用bio的写法写nio
  - 纯NIO2 [CoroutineNio2Demo](src/test/kotlin/com/example/javaniodemo/demo/CoroutineNio2Demo.kt)
    - coroutine对接nio2
    - 可以对比下JavaNio2Demo写法，二者的功能一模一样，仅仅是做了kotlin协程的对接，就少了3层回调；
      apiRequest代码量从79行缩减到29行，就算加上可以复用的工具方法的11行，也就40行；
      并且命令式顺序执行，逻辑也清晰了很多
- java loom 协程
  - hutool bio[LoomHutoolBioDemo](loom-demo/src/test/java/org/example/demo/loom/loomtest/LoomHutoolBioDemo.java)
    - loom下bio直接能够线程复用，直接跑在VirtualThread线程池就行
    - 可以直接非阻塞化的原阻塞逻辑：[Loom: Blocking Operations](https://wiki.openjdk.java.net/display/loom/Blocking+Operations)
- 【对照组】bio
  - 纯socket [JavaBioDemo](src/test/kotlin/com/example/javaniodemo/demo/JavaBioDemo.java)
  - hutool HttpUtil [JavaHutoolBioDemo](src/test/kotlin/com/example/javaniodemo/demo/JavaHutoolBioDemo.java)
  - feign [JavaFeignBioDemo](src/test/kotlin/com/example/javaniodemo/demo/JavaFeignBioDemo.java)
  - 以下客户端效果相同，没做实现：
     - okhttp
     - apache http

#### 代码结构说明
- setUp：初始化junit单元测试，做些配置
- singleTest：验证单个请求
- multiTest：单线程并发逻辑实现【主逻辑】
- singleThreadClient：创建单线程客户端
- apiRequest：单次http api请求

## nio的几个误区
不是靠猜，写代码验证

### nio2、同步、异步、非阻塞
[维基百科 - Java NIO](https://zh.wikipedia.org/wiki/Java_NIO)

- nio是非阻塞io；
- 非阻塞中，阻塞的是线程；
- 误区：有人把NIO.2叫aio，区分于nio，说nio是同步非阻塞、aio是异步非阻塞，再配上几张看不懂的图
  - 解答：其实无所谓怎么说，先写代码验证，通过代码跑的结果看特性，通过这里的代码可以看出来：
    - 官方并没有什么同步异步的说法，nio2仅仅是nio新封装了几个更易用的api；
      - 当然了，关于文件系统读写有了些新功能，这里暂不讨论，只讨论socket、网络io，基本上我们只写无状态应用。
    - 跟spring mvc封装servlet、reactor-netty封装netty、netty封装nio类似，nio2其实用起来写代码的效果也跟netty封装nio类似，nio2算是java自带的netty框架。
    - 只用nio依旧能实现上述功能，说明nio并不是同步的；
      - 跑在一个循环里面，异步逻辑由selector制造，只不过写法巨麻烦。
    - 使用nio2实现上面功能更简单，写法与netty类似。

### 拿netty写代码遇到future直接就sync()了
可以看sync()方法的注释
```
    /**
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     */
    Future<V> sync() throws InterruptedException;
```
这个方法是阻塞线程、直到future完成，调了这个方法使用netty就和非阻塞无缘了，就是用bio的方式用netty，使用效果和bio一致。

估计是因为类似差不多的原因，vertx的future直接就不提供get()这样的阻塞线程等结果的方法。

### 非阻塞代码里面加入阻塞式的会怎样 
不是靠猜，写代码验证

[为什么spring cloud gateway里面不能有阻塞调用？](http://confluence.k8s.fingard.cn/pages/viewpage.action?pageId=20452882)

可以尝试下，分别在 bio、nio的demo的apiRequest加上一句，然后运行看效果：
```java
// import cn.hutool.http.HttpUtil;
HttpUtil.get("http://localhost:8080/delay5s");
```

### reactive在java真正意义上的作用
> - 这里只解读其必要性，而不是可用可不用的那种有得选；
> - 至于反应式的优点：跟普通命令式顺序编程比起来，反应式编程没有任何优点；
> - 缺点：难写、难看、难调、难用、出问题不好排查；
> - 即便没有优点，但其对于java下非阻塞nio编程是有必要性。

一些误解、错误印象：[重新理解响应式编程](https://www.jianshu.com/p/c95e29854cb1) ，说是反应式优点是能更好的组织代码逻辑。

解答：
1. 话说为啥都叫响应式啊？应该是reactive、而不是responsive；
2. 反应式下代码逻辑是非常难写的，回调套回调的方式比命令式顺序编程难度大非常多倍；
3. 其实好处并不是这个文章的东西，文章里说的完全可以用顺序逻辑写，顶多外面套一个观察者模式；完全是因为java下没有让线程跳出当前方法执行语句的关键字，去执行别的，过段时间在回来执行；才产生了这么些不伦不类的试图替代java调度逻辑的库reactor、rx，下面是结合这里的一些非阻塞nio编程demo的详细描述。

现在java下没协程，loom迟迟未发版，造成了java以下逻辑不能实现：线程执行到某行代码，让线程等待一段时间、线程去执行其他逻辑、然后线程再回来执行这里的下一行代码。唯一的解决方法是把下一行代码以及之后的代码放在lambda表达式里面、也就是回调，让这个方法结束掉，后面再通过某些逻辑触发回调，只有这样才能实现非阻塞。

以nio的selector为例【[JavaNioDemo](src/test/kotlin/com/example/javaniodemo/demo/JavaNioDemo.java)】 ，通常的方法、回调的调度逻辑，也就是一个大大的循环裹住一切，这个循环有且仅有一个阻塞点，也就是事件触发的地方等待事件、没有事件时阻塞，否则循环空转会导致这个线程的cpu单核消耗100%，这个循环扩大意义范围就统称事件循环。

java自带的CompletableFuture就是对这样的回调逻辑的封装【[Jdk11HttpClientNioDemo](src/test/kotlin/com/example/javaniodemo/demo/Jdk11HttpClientNioDemo.java) 】，spring mono、vertx算是对CompletableFuture功能的扩展，本质上并没有差别，就是回调。至于扩展的这一部分调度逻辑的编程方式，来处理回调式（也称为函数式）逻辑的if、else、while、for、finally、catch这样的逻辑语义，大体上统称为反应式/reactive编程。至于这个[反应式宣言](https://www.reactivemanifesto.org/zh-CN) ，只是前面reactive编程理想状态下想要达成的效果，其实也无所谓，仅仅是一种编程方式而已。

至于为啥java下各路软件框架都一直强调reactive？
因为java下reactive是非阻塞nio编程的唯一方式，reactive是非阻塞nio编程一步步封装下来的结果，是java下非阻塞nio编程所必要的。也可以不用reactor、vertx这样的框架来写非阻塞nio，自己封装各种回调，其实自己封装的最终结果就是造了一个功能不完整的reactive框架。

效果：在java下就不要考虑什么非阻塞了，更不要考虑非阻塞的业务逻辑，所耗费的精力、代码难度、实现进度等，比学一个原生支持非阻塞语义的jvm语言再做实现，难度高太多了。

结论：java下就用阻塞式，真要非阻塞就换其他jvm语言，反正双向对接非常方便。比如kotlin的协程，非常轻松的就用命令式的写法对接nio2【[CoroutineNio2Demo](src/test/kotlin/com/example/javaniodemo/demo/CoroutineNio2Demo.kt)】，当然也有原厂的nio框架【[CoroutineKtorNioDemo](src/test/kotlin/com/example/javaniodemo/demo/CoroutineKtorNioDemo.kt)】

#### 协程的实际意义上作用
具备反应式的所有功能、能写非阻塞nio代码的同时，用的是命令式顺序写法。

## loom试验
[loom-demo](./loom-demo)

### 编译说明

javac 编译参数添加：--enable-preview

#### idea配置
- idea 设置
  - 构建、执行、部署
    - 构建工具
      - 编译器
        - java编译器
          - Javac选项
            - 按模块重写编译器参数
              - +号：添加loom-demo
                - loom-demo编译选项：--enable-preview

按以上步骤设置好之后，idea直接点单元测试、或者main方法的运行图标，就都能正常运行了。

## 代码行数

| 代码                        | 文件总行数（算空行、注释） | 代码行数 |
| --------------------------- | -------------------------- | -------- |
| JavaNioDemo.java            | 213                        | 150      |
| JavaNio2Demo.java           | 168                        | 129      |
| NettyNioDemo.java           | 139                        | 113      |
| JavaFeignBioDemo.java       | 97                         | 76       |
| JavaBioDemo.java            | 93                         | 74       |
| VertxHttpClientNioDemo.java | 103                        | 73       |
| SpringWebClientNioDemo.java | 110                        | 69       |
| Jdk11HttpClientNioDemo.java | 91                         | 69       |
| CoroutineCioNioDemo.kt      | 92                         | 68       |
| CoroutineMonoNioDemo.kt     | 81                         | 65       |
| CoroutineKtorNioDemo.kt     | 68                         | 53       |
| JavaHutoolBioDemo.java      | 66                         | 48       |


## TODO
- [x] coroutine对接nio2，也可以叫《My CIO》
- [x] nio通过按顺序多次设置监听，解决循环空转问题
