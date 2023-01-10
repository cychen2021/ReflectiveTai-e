- 每个测试用例包含1个（或更多）Method.invoke(Object,Object[])调用，且每个invoke调用都是由main方法可达的

- 每个invoke调用的注释都标明了反射分析Solar对于该调用的预期分析结果；如果分析结果与注释不一致，说明没有正确实现分析规则

- 每个测试用例都是可运行的，你也可以运行用例观察其输出

- 你在Tai-e上实现反射分析的结果应当与你在Soot/WALA上实现分析的结果一致

- 以下用例可用于测试类名与方法名都可知（字符串常量）的情况 (覆盖[P-ForName], [P-GetMtd], [T-Inv])
  - Basic.java
  - Inheritance.java
  - DuplicateName.java
  - ArgRefine.java

- 以下用例可用于测试类名可知，但方法名未知的情况 (覆盖[P-ForName], [P-GetMtd], [I-InvSig], [T-Inv]，主要测试[I-InvSig])
  - UnknownMethodName.java
  - GetMethods.java

- 以下用例可用于测试类名未知，但方法名以及调用点参数类型可知的情况 (覆盖[P-ForName], [P-GetMtd], [I-InvTp], [I-InvS2T], [T-Inv]，主要测试[I-InvTp]与[I-InvS2T])
  - RecvType.java
  - UnknownClassName.java
