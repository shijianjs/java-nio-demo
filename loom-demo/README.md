# loom试验

## 编译说明

javac 编译参数添加：--enable-preview

idea不支持java 19 preview语言级别，就用maven编译，idea执行；

### idea配置
- idea 设置
  - 构建、执行、部署
    - 构建工具
      - Maven
        - 运行程序
          - 勾选上：将IDE构建/运行操作委托给Maven
          - VM选项：--enable-preview

按以上步骤设置好之后，idea直接点单元测试、或者main方法的运行图标，就都能正常运行了。