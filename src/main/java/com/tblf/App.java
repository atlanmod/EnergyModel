package com.tblf;

import com.tblf.behavior.EnergyBehavior;
import com.tblf.business.AnalysisLauncher;
import com.tblf.instrumentation.InstrumentationType;
import com.tblf.junitrunner.MavenRunner;
import com.tblf.parsing.TraceType;
import com.tblf.parsing.parsers.Parser;
import com.tblf.parsing.traceReaders.TraceFileReader;
import com.tblf.processors.ClassProcessor;
import com.tblf.utils.Configuration;
import com.tblf.utils.ModelUtils;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.omg.smm.CollectiveMeasurement;
import org.omg.smm.DimensionalMeasurement;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

/**
 * Hello world!
 */
public class App {
    private static CollectiveMeasurement root;

    public static void main(String[] args) throws URISyntaxException {
        if (args.length == 0)
            System.exit(1);

        File file = new File(args[0]);

        new MavenRunner(new File(file, "pom.xml")).compilePom();

        AnalysisLauncher analysisLauncher = new AnalysisLauncher(file);
        analysisLauncher.setInstrumentationType(InstrumentationType.BYTECODE);

        analysisLauncher.setTraceType(TraceType.FILE);

        analysisLauncher.registerDependencies(Collections.singletonList(new File("pom.xml")));

        analysisLauncher.registerProcessor(new ClassProcessor());

        analysisLauncher.applyAfter(file1 -> {
            File trace = new File(file1, Configuration.getProperty("traceFile"));
            ResourceSet resourceSet = ModelUtils.buildResourceSet(file1);
            new Parser(new TraceFileReader(trace), new EnergyBehavior(resourceSet)).parse();
            Resource resource = resourceSet.getResources().get(resourceSet.getResources().size() - 1);
            try {
                resource.save(Collections.EMPTY_MAP);
            } catch (IOException e) {
                e.printStackTrace();
            }

            resource.getAllContents().forEachRemaining(eObject -> {
                //get the root method calls
                if (eObject instanceof CollectiveMeasurement && ((CollectiveMeasurement) eObject).getBaseMeasurementFrom().isEmpty()) {
                    displayCallGraph((CollectiveMeasurement) eObject);
                }
            });
        });


        analysisLauncher.run();
    }

    private static void displayCallGraph(CollectiveMeasurement root) {

        //System.out.println(root.getName()+": Raw Energy: "+getEnergy(root)+ " : individual energy "+getIndividualMethodEnergy(root));
        System.out.println(root.getName() + " : " + getEnergy(root) + " : " + getIndividualMethodEnergy(root));

        root.getBaseMeasurementTo().forEach(baseNMeasurementRelationship -> {
            System.out.print("\t -");
            displayCallGraph((CollectiveMeasurement) baseNMeasurementRelationship.getTo());
        });

    }

    /**
     * Get the energy of a Measurement, (Excludes all the sub-measurement energy)
     *
     * @param root
     * @return
     */
    private static double getIndividualMethodEnergy(CollectiveMeasurement root) {
        return getEnergy(root) - root
                .getBaseMeasurementTo().stream()
                .map(baseNMeasurementRelationship -> getEnergy((CollectiveMeasurement) baseNMeasurementRelationship.getTo()))
                .mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Get the Energy of a measurement (Includes all the sub-measurement energy)
     *
     * @param measurement
     * @return
     */
    private static double getEnergy(CollectiveMeasurement measurement) {
        return ((DimensionalMeasurement)
                measurement.getMeasurementRelationships()
                        .stream()
                        .filter(measurementRelationshipPredicate -> measurementRelationshipPredicate.getName() != null
                                && measurementRelationshipPredicate.getName().equals("energy"))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No energy measurement found"))
                        .getTo()).getValue();
    }
}
