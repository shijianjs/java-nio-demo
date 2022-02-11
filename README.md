# java io
[维基百科 - Java NIO](https://zh.wikipedia.org/wiki/Java_NIO)

以多种方式实现非阻塞demo（单线程100并发），实现的方式有：
java nio、java nio2、jdk11 HttpClient、Spring WebFlux WebClient、Vert.x HttpClient、
kotlin coroutine + ktor + cio、kotlin coroutine + webflux；

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
  - 纯nio [JavaNioDemo](src/test/kotlin/com/example/javaniodemo/demo/JavaNioDemo.java)、nio2 [JavaNio2Demo](src/test/kotlin/com/example/javaniodemo/demo/JavaNio2Demo.java)
    - 这里踩了下坑，网上大部分教程读作nio，写成bio，包括oracle官方教程、baeldung大佬的教程、各路csdn文章，根本就没必要非阻塞效果、一个io严格对应1+个线程
    - 这里的demo代码才是nio的正确写法
  - jdk11 http [Jdk11HttpClientNioDemo](src/test/kotlin/com/example/javaniodemo/demo/Jdk11HttpClientNioDemo.java)
- java语法下的外部库回调式、外置事件循环io框架（函数式非阻塞）
  - 纯netty [NettyNioDemo](src/test/kotlin/com/example/javaniodemo/demo/NettyNioDemo.java)
  - spring mono [SpringWebClientNioDemo](src/test/kotlin/com/example/javaniodemo/demo/SpringWebClientNioDemo.java)
  - Vert.x [VertxHttpClientNioDemo](src/test/kotlin/com/example/javaniodemo/demo/VertxHttpClientNioDemo.java)
- kotlin协程
  - mono coroutine [CoroutineMonoNioDemo](src/test/kotlin/com/example/javaniodemo/demo/CoroutineMonoNioDemo.kt)
  - ktor [CoroutineKtorNioDemo](src/test/kotlin/com/example/javaniodemo/demo/CoroutineKtorNioDemo.kt)
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

## 非阻塞代码里面加入阻塞式的会怎样 
不是靠猜，写代码验证

[为什么spring cloud gateway里面不能有阻塞调用？](http://confluence.k8s.fingard.cn/pages/viewpage.action?pageId=20452882)

可以尝试下，分别在 bio、nio的demo的apiRequest加上一句，然后运行看效果：
```java
// import cn.hutool.http.HttpUtil;
HttpUtil.get("http://localhost:8080/delay5s");
```

