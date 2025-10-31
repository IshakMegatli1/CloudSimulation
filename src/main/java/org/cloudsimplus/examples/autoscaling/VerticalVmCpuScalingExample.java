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
import org.cloudbus.cloudsim.resources.Processor;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScalingSimple;
import org.cloudsimplus.autoscaling.resources.ResourceScaling;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingDouble;

public class VerticalVmCpuScalingExample {

    private static final int SCHEDULING_INTERVAL = 1;
    private static final int HOSTS = 1;

    // ✅ Modifié : 8 cœurs CPU par hôte (au lieu de 32)
    private static final int HOST_PES = 8;

    // ✅ Modifié : 2 VMs au lieu d’une seule
    private static final int VMS = 2;

    // ✅ Modifié : 2 PEs par VM (au lieu de 14)
    private static final int VM_PES = 2;

    private static final int VM_MIPS = 1000;

    // ✅ Modifié : RAM VM à 1000 MB
    private static final int VM_RAM = 1000;

    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    // ✅ Modifié : 80 cloudlets au total
    private static final int CLOUDLETS = 80;

    private int createsVms;

    public static void main(String[] args) {
        new VerticalVmCpuScalingExample();
    }

    private VerticalVmCpuScalingExample() {
        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(CLOUDLETS);

        simulation = new CloudSim();
        simulation.addOnClockTickListener(this::onClockTickListener);

        createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList.addAll(createListOfScalableVms(VMS));

        // ✅ Nouvelle méthode de génération des cloudlets
        createCloudletsInBatches();

        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();
        printSimulationResults();
    }

    private void onClockTickListener(EventInfo evt) {
        vmList.forEach(vm ->
                System.out.printf(
                        "\t\tTime %6.1f: Vm %d CPU Usage: %6.2f%% (%2d vCPUs. Running Cloudlets: #%d). RAM usage: %.2f%% (%d MB)%n",
                        evt.getTime(), vm.getId(), vm.getCpuPercentUtilization() * 100.0, vm.getNumberOfPes(),
                        vm.getCloudletScheduler().getCloudletExecList().size(),
                        vm.getRam().getPercentUtilization() * 100, vm.getRam().getAllocatedResource())
        );
    }

    private void printSimulationResults() {
        final var finishedCloudletList = broker0.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getExecStartTime);
        finishedCloudletList.sort(sortByVmId.thenComparing(sortByStartTime));
        new CloudletsTableBuilder(finishedCloudletList).build();
    }

    private void createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }
        final var dc0 = new DatacenterSimple(simulation, hostList);
        dc0.setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000));
        }

        final long ram = 20000;
        // ✅ Modifié : Bande passante = 20000 au lieu de 100000
        final long bw = 20000;
        final long storage = 10000000;

        return new HostSimple(ram, bw, storage, peList)
                .setVmScheduler(new VmSchedulerTimeShared());
    }

    private List<Vm> createListOfScalableVms(final int vmsNumber) {
        final var newVmList = new ArrayList<Vm>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            final var vm = createVm();
            vm.setPeVerticalScaling(createVerticalPeScaling());
            newVmList.add(vm);
        }
        return newVmList;
    }

    private Vm createVm() {
        final int id = createsVms++;
        // ✅ Ajouté : CloudletSchedulerTimeShared pour chaque VM
        return new VmSimple(id, VM_MIPS, VM_PES)
                .setRam(VM_RAM)
                .setBw(1000)
                .setSize(10000)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private VerticalVmScaling createVerticalPeScaling() {
        final double scalingFactor = 0.1;
        final var verticalCpuScaling = new VerticalVmScalingSimple(Processor.class, scalingFactor);
        verticalCpuScaling.setResourceScaling(vs -> 2 * vs.getScalingFactor() * vs.getAllocatedResource());
        verticalCpuScaling.setLowerThresholdFunction(this::lowerCpuUtilizationThreshold);
        // ✅ Modifié : Seuil de surcharge CPU = 0.7 (70%)
        verticalCpuScaling.setUpperThresholdFunction(vm -> 0.7);
        return verticalCpuScaling;
    }

    private double lowerCpuUtilizationThreshold(Vm vm) {
        return 0.4;
    }

    // ✅ Nouvelle méthode : 20 cloudlets toutes les 4 secondes, tailles progressives
    private void createCloudletsInBatches() {
        int total = 0;
        for (int batch = 0; batch < 4; batch++) {
            for (int i = 0; i < 20; i++) {
                long length = 10000 + total * 10000; // 10000 → 200000
                cloudletList.add(createCloudlet(length, 2, batch * 4.0));
                total++;
            }
        }
    }

    private Cloudlet createCloudlet(final long length, final int pesNumber, final double delay) {
        final UtilizationModel utilizationCpu = new UtilizationModelFull();
        final var utilizationModelDynamic = new UtilizationModelDynamic(1.0 / CLOUDLETS);

        final var cl = new CloudletSimple(length, pesNumber);
        cl.setFileSize(1024)
                .setOutputSize(1024)
                .setUtilizationModelBw(utilizationModelDynamic)
                .setUtilizationModelRam(utilizationModelDynamic)
                .setUtilizationModelCpu(utilizationCpu)
                .setSubmissionDelay(delay);
        return cl;
    }
}
