package com.tblf.behavior;

import com.tblf.parsing.parsingBehaviors.ParsingBehavior;
import com.tblf.utils.ModelUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.gmt.modisco.java.AbstractMethodDeclaration;
import org.eclipse.gmt.modisco.java.ConstructorDeclaration;
import org.omg.smm.*;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class EnergyBehavior extends ParsingBehavior {
    private Map<String, AbstractMethodDeclaration> stringMethodDeclarationMap;
    private static final SmmFactory FACTORY = SmmFactory.eINSTANCE;

    private Observation observation;

    private Measure collectiveMeasure;

    private Stack<CollectiveMeasurement> methodStack;

    public EnergyBehavior(ResourceSet model) {
        super(model);
        Resource jModel = model.getResources().stream().filter(resource -> resource.getURI().toString().contains("_java.xmi")).findFirst().get();


        stringMethodDeclarationMap = new HashMap<>();
        methodStack = new Stack<>();

        //Build an index of the methods
        jModel.getAllContents().forEachRemaining(eObject -> {
            if (eObject instanceof AbstractMethodDeclaration) {
                if (eObject instanceof ConstructorDeclaration) {
                    ConstructorDeclaration constructorDeclaration = (ConstructorDeclaration) eObject;
                    constructorDeclaration.setName("<init>");
                }
                String qn = ModelUtils.getQualifiedName(eObject);
                stringMethodDeclarationMap.put(qn, (AbstractMethodDeclaration) eObject);
            }
        });

        SmmPackage.eINSTANCE.eClass();

        MeasureLibrary measureLibrary = FACTORY.createMeasureLibrary();

        UnitOfMeasure uj = FACTORY.createUnitOfMeasure();
        uj.setName("uj");
        uj.setDescription("MicroJoule energy unit");

        DirectMeasure energyMeasure = FACTORY.createDirectMeasure();
        energyMeasure.setName("energy");
        energyMeasure.setUnit(uj);

        UnitOfMeasure ns = FACTORY.createUnitOfMeasure();
        ns.setName("ns");
        ns.setDescription("Nanoseconds time duration");

        DirectMeasure timeMeasure = FACTORY.createDirectMeasure();
        timeMeasure.setName("duration");
        timeMeasure.setUnit(ns);

        collectiveMeasure = FACTORY.createCollectiveMeasure();

        MeasureRelationship measureRelationshipToEnergyMeasure = FACTORY.createBaseNMeasureRelationship();
        measureRelationshipToEnergyMeasure.setFrom(collectiveMeasure);
        measureRelationshipToEnergyMeasure.setTo(energyMeasure);
        measureRelationshipToEnergyMeasure.setName("collectToEnergyMeasure");

        MeasureRelationship measureRelationshipToDurationMeasure = FACTORY.createBaseNMeasureRelationship();
        measureRelationshipToDurationMeasure.setFrom(collectiveMeasure);
        measureRelationshipToDurationMeasure.setTo(timeMeasure);
        measureRelationshipToDurationMeasure.setName("collectToTimeMeasure");

        collectiveMeasure.getMeasureRelationships().addAll(Arrays.asList(measureRelationshipToDurationMeasure, measureRelationshipToEnergyMeasure));

        measureLibrary.getMeasureElements().addAll(Arrays.asList(collectiveMeasure, energyMeasure, timeMeasure, uj, ns));

        SmmModel smmModel = FACTORY.createSmmModel();
        smmModel.getLibraries().add(measureLibrary);

        observation = FACTORY.createObservation();

        smmModel.getObservations().add(observation);

        try {
            Resource resource = new XMIResourceImpl();
            resource.setURI(URI.createURI(new File("energy.xmi").toURI().toURL().toString()));
            resource.getContents().add(smmModel);
            model.getResources().add(resource);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void manage(String trace) {
        String[] fields = trace.split(";");
        String QN = fields[0];

        if (fields.length == 3) { //end of method
            String uj = fields[1];
            String ns = fields[2];

            CollectiveMeasurement collectiveMeasurement = methodStack.pop();

            collectiveMeasurement.getMeasurementRelationships().forEach(baseNMeasurementRelationship -> {
                DimensionalMeasurement dimensionalMeasurement = (DimensionalMeasurement) baseNMeasurementRelationship.getTo();

                if (dimensionalMeasurement.getName().equals("energy"))
                    dimensionalMeasurement.setValue(Double.parseDouble(uj));

                else if (dimensionalMeasurement.getName().equals("duration"))
                    dimensionalMeasurement.setValue(Double.parseDouble(ns));

            });

        } else {
            //start of method
            AbstractMethodDeclaration methodDeclaration = stringMethodDeclarationMap.get(QN);

            CollectiveMeasurement collectiveMeasurement = createRootMeasurement(methodDeclaration);
            collectiveMeasurement.setName(QN);
            DirectMeasurement energy = createEnergyMeasurement();
            DirectMeasurement duration = createDurationMeasurement();

            addRelationShip(collectiveMeasurement, energy);
            addRelationShip(collectiveMeasurement, duration);

            ObservedMeasure observedMeasure = FACTORY.createObservedMeasure();
            observedMeasure.getMeasurements().addAll(Arrays.asList(collectiveMeasurement, energy, duration));
            observedMeasure.setMeasure(collectiveMeasure);

            observation.getObservedMeasures().add(observedMeasure);

            methodStack.push(collectiveMeasurement);
        }
    }

    private void addRelationShip(CollectiveMeasurement from, DimensionalMeasurement to) {
        BaseNMeasurementRelationship relationship = FACTORY.createBaseNMeasurementRelationship();

        relationship.setFrom(from);
        relationship.setTo(to);
        relationship.setName(to.getName());

        from.getMeasurementRelationships().add(relationship);
    }

    /**
     * Create the {@link CollectiveMeasurement} containings the child {@link DimensionalMeasurement}.
     * Also set up the call relationship between methods
     *
     * @param measurand an {@link EObject}, usually a {@link org.eclipse.gmt.modisco.java.MethodDeclaration}
     * @return a {@link CollectiveMeasurement}
     */
    private CollectiveMeasurement createRootMeasurement(EObject measurand) {
        CollectiveMeasurement collectiveMeasurement = FACTORY.createCollectiveMeasurement();
        collectiveMeasurement.setMeasurand(measurand);


        if (!methodStack.empty()) {
            CollectiveMeasurement previous = methodStack.peek();

            BaseNMeasurementRelationship baseNMeasurementRelationship = FACTORY.createBaseNMeasurementRelationship();
            baseNMeasurementRelationship.setName("call");

            baseNMeasurementRelationship.setTo(collectiveMeasurement);
            baseNMeasurementRelationship.setFrom(previous);

            previous.getBaseMeasurementTo().add(baseNMeasurementRelationship);
            collectiveMeasurement.getBaseMeasurementFrom().add(baseNMeasurementRelationship);
            collectiveMeasurement.getMeasurementRelationships().add(baseNMeasurementRelationship);
        }

        return collectiveMeasurement;
    }

    private DirectMeasurement createDurationMeasurement() {
        DirectMeasurement directMeasurementUj = FACTORY.createDirectMeasurement();
        directMeasurementUj.setName("duration");
        return directMeasurementUj;
    }

    private DirectMeasurement createEnergyMeasurement() {
        DirectMeasurement directMeasurementUj = FACTORY.createDirectMeasurement();
        directMeasurementUj.setName("energy");
        return directMeasurementUj;
    }
}
