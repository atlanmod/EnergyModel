
# Characterizing a Source code model with energy metrics

## Approach

This repository describes our approach for generating source code model characterized with energy metrics.
A model is generated using the MoDisco framework. The source code is instrumented and executed to gather metrics dynamically. 
Finally the metrics are analyzed, modeled using the Structured Metrics Meta-model (SMM) and are finally linked to the Java Model.

### Java Model

[MoDisco](https://www.eclipse.org/MoDisco/) is used for generating the model. This model conforms to a Java meta-model specified with EMF Ecore. MoDisco works as an Eclipse Plugin, not included in this project. 

### Instrumentation

The source code analyzed is instrumented using the [ASM Library](https://asm.ow2.io/). The processor used is defined in the Java class EnergyProcessor. 
It works as follow:

```
@Override  
protected void onMethodEnter() {  
	super.onMethodEnter();  
	mv.visitTypeInsn(Opcodes.NEW, "com/tblf/monitor/RAPLMonitor");  
	mv.visitInsn(Opcodes.DUP);  
	mv.visitLdcInsn(className + "$" + name);  
	mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/tblf/monitor/RAPLMonitor", "<init>", "(Ljava/lang/String;)V", false);
	value = newLocal(Type.getType(RAPLMonitor.class));  
	mv.visitVarInsn(Opcodes.ASTORE, value);  
}
```
The Bytecode specified here is added at the entry point of all the methods under analysis. The class `RAPLMonitor` is created, hence starting the monitoring.

```
@Override  
protected void onMethodExit(int opcode) {  
	mv.visitVarInsn(Opcodes.ALOAD, value);  
	mv.visitLdcInsn(className + "$" + name);  
	mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/tblf/monitor/RAPLMonitor", "report", "(Ljava/lang/String;)V", false);  
	super.onMethodExit(opcode);  
}
```

This Bytecode is added at all the exit points of the methods under analysis. This ends the monitoring of the method and report the energy it consumed, and its duration, in a messaging queue. 

The monitor called here is used to get the energy consumed by the CPU using Intel's Running Average Power Limit. This class has been written according to the [jRAPL](http://kliu20.github.io/jRAPL/) library. 

Executing the program under analysis will produce traces that are written in a messaging queue for analyzing later. Computing the analysis *during* the execution of the program would create a major overhead, deeply impacting the accuracy of the measurements. 

In order to run the instrumented code, we simply run the JUnit test cases of the program.

### Analysis 

The analysis is performed in the `EnergyBehavior` class. The constructor initializes the SMM model, instantiates the Measures, and indexes the methods for accelerating the analysis.

The `manage(trace)` method defines the behavior.  For each duration and energy measurement, a `DimensionalMeasurement` is created in the SMM instance. For simplicity purposes, for each method, those measurements are joined in a `CollectiveMeasurement` element. 

This `CollectiveMeasurement` is linked to the `MethodDeclaration` it corresponds to in the MoDisco Java model using the  `measurand` relationship. 

Finally,  the call graph of the program execution is added in the model by adding `MeasurementRelationships` between the `CollectiveMeasurements`.

The following example is used to describe our approach.

## Example

### The program

The archive contained in `/src/test/resouces/SimpleProject.zip` contains a Java program that will be used for our analysis. 

This program consists in an interface `IApp`, a class `App` inheriting from this interface, and a JUnit4 test suite verifying `App` called `AppTest`.

### Generating the Java model

Running MoDisco on this project generates the following Java Model: 
![](pics/java_model.png?raw=true "Java Model")

This model will be characterized with energy metrics later.

### Running the instrumented code

After instrumenting it, the code is executed. A trace is produced in the `/src/test/resources/SimpleProject/trace`  directory. 

A trace is produced, and looks to that: 
```
AppTest$<init>  
AppTest$<init>;1526;117295  
AppTest$testApp  
App$<init>  
App$<init>;1281;183053  
App$method  
App$method;1526;126900  
AppTest$testApp;11841;1536954  
AppTest$<init>  
AppTest$<init>;610;91462  
AppTest$testApp2  
App$<init>  
App$<init>;1953;192886  
App$method  
App$method;1892;109746  
AppTest$testApp2;7629;695872
```
Where `AppTest$<Init>` corresponds to the class and the method name. This is then used to find them in the Java model. When no number is specified, that's because the method has been entered in. When values are specified, then the method has been exited from, and energy and duration have been gathered.

For instance, running `AppTest$<init>` consumed 1526 microJoules and lasted 117295 nanoseconds.

### Analyzing the traces

The EnergyBehavior class analyses the traces and generates the SMM model with it. With the trace specified before, the following model is generated: 

![](pics/smmmodel.png?raw=true "SMM Model")

The two red boxes represent the instantiations of the test class for the two test methods `testApp` and `testApp2`. The green box is the execution of the `testApp` test case, with the energy consumed by `testApp`, `App$init` and `App.method`. 
Finally the blue box represents the execution of the second test case `testApp2`, with the energy consumed by `testApp2` method, `App.init` and `App.method`.

### Querying the Model 
