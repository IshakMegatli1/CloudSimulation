package org.cloudsimplus.examples.autoscaling;

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScalingSimple;
import org.cloudsimplus.autoscaling.resources.ResourceScaling;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;

import java.util.ArrayList;
import java.util.List;

public class VerticalScalingExample {
    private static final int SCHEDULING_INTERVAL = 1;
    private static final int HOSTS = 1;
    private static final int HOST_PES = 8;
    private static final long HOST_RAM = 20000;
    private static final long HOST_BW = 20000;
    private static final long HOST_STORAGE = 10000000;

    private static final int VMS = 2;
    private static final int VM_PES = 2;
    private static final int VM_RAM = 1000;
    private static final int VM_MIPS = 1000;
    private static final long VM_BW = 1000;
    private static final long VM_SIZE = 10000;

    private static final int CLOUDLETS_PER_BATCH = 20;
    private static final int TOTAL_CLOUDLETS = 80;
    private static final int CLOUDLET_MIN_LENGTH = 10000;
    private static final int CLOUDLET_MAX_LENGTH = 200000;
    private static final int CLOUDLET_STEP = 10000;

    private final CloudSim simulation;
    private DatacenterBroker broker;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    private int vmCounter = 0;

    public static void main(String[] args) {
        new VerticalScalingExample();
    }

    private VerticalScalingExample() {
        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(TOTAL_CLOUDLETS);

        simulation = new CloudSim();
        simulation.addOnClockTickListener(this::onClockTickListener);

        createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);

        vmList.addAll(createScalableVms(VMS));
        broker.submitVmList(vmList);

        createCloudletsBatches();
        broker.submitCloudletList(cloudletList);

        simulation.start();
        printResults();
    }

    private void onClockTickListener(EventInfo evt) {
        vmList.forEach(vm ->
                System.out.printf(
                        "Time %.1f: Vm %d CPU Usage: %.2f%% (%d vCPUs, Cloudlets: %d)%n",
                        evt.getTime(), vm.getId(), vm.getCpuPercentUtilization() * 100.0,
                        vm.getNumberOfPes(), vm.getCloudletScheduler().getCloudletExecList().size()
                )
        );
    }

    private void printResults() {
        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
    }

    private void createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }
        Datacenter dc = new DatacenterSimple(simulation, hostList);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        List<Pe> peList = new ArrayList<>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList)
                .setVmScheduler(new VmSchedulerTimeShared());
    }

    private List<Vm> createScalableVms(int count) {
        List<Vm> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Vm vm = new VmSimple(vmCounter++, VM_MIPS, VM_PES)
                    .setRam(VM_RAM)
                    .setBw(VM_BW)
                    .setSize(VM_SIZE)
                    .setCloudletScheduler(new CloudletSchedulerTimeShared());

            vm.setPeVerticalScaling(createVerticalCpuScaling());
            list.add(vm);
        }
        return list;
    }

    private VerticalVmScaling createVerticalCpuScaling() {
        double scalingFactor = 0.1;
        VerticalVmScalingSimple scaling = new VerticalVmScalingSimple(org.cloudbus.cloudsim.resources.Processor.class, scalingFactor);

        // Définir seuil CPU pour scaling vertical
        scaling.setLowerThresholdFunction(vm -> 0.0);  // on ne réduit pas dans ce cas
        scaling.setUpperThresholdFunction(vm -> 0.7);  // si CPU > 70% on augmente

        // Scaling graduel
        scaling.setResourceScaling(vs -> vs.getScalingFactor() * vs.getAllocatedResource());

        return scaling;
    }

    private void createCloudletsBatches() {
        double delay = 0.0;
        for (int length = CLOUDLET_MIN_LENGTH; length <= CLOUDLET_MAX_LENGTH; length += CLOUDLET_STEP) {
            for (int i = 0; i < CLOUDLETS_PER_BATCH; i++) {
                cloudletList.add(createCloudlet(length, VM_PES, delay));
            }
            delay += 4.0; // chaque batch arrive toutes les 4 secondes
        }
    }

    private Cloudlet createCloudlet(long length, int pes, double delay) {
        // CPU : utilisation totale
        UtilizationModel utilizationCpu = new UtilizationModelFull();
        // RAM et bande passante : utilisation dynamique partagée
        UtilizationModelDynamic utilizationBwRam = new UtilizationModelDynamic(1.0 / TOTAL_CLOUDLETS);

        // Création du Cloudlet
        Cloudlet cloudlet = new CloudletSimple(length, pes);

        // Configuration séparée de chaque propriété
        cloudlet.setFileSize(1024);
        cloudlet.setOutputSize(1024);
        cloudlet.setUtilizationModelCpu(utilizationCpu);
        cloudlet.setUtilizationModelBw(utilizationBwRam);
        cloudlet.setUtilizationModelRam(utilizationBwRam);
        cloudlet.setSubmissionDelay(delay);

        return cloudlet;
    }

}
