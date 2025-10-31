package org.cloudsimplus.examples.autoscaling;

import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

import java.util.ArrayList;
import java.util.List;

public class simpleExemple {

    public static void main(String[] args) {
        // Crée la simulation CloudSim
        CloudSim simulation = new CloudSim();

        // Crée un datacenter avec 1 hôte
        Datacenter datacenter = createDatacenter(simulation);

        // Crée un broker
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);

        // Crée 2 VMs
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Vm vm = new VmSimple(1000, 2); // 1000 MIPS, 2 cœurs
            vm.setRam(1000).setBw(1000).setSize(10000);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }

        // Crée 5 cloudlets
        List<Cloudlet> cloudletList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Cloudlet cloudlet = new CloudletSimple(10000 * (i + 1), 2); // Taille croissante
            cloudlet.setFileSize(300).setOutputSize(300);
            UtilizationModel utilization = new UtilizationModelFull();
            cloudlet.setUtilizationModelCpu(utilization)
                    .setUtilizationModelRam(utilization)
                    .setUtilizationModelBw(utilization);
            cloudletList.add(cloudlet);
        }

        // Soumet les listes au broker
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        // Lance la simulation
        simulation.start();

        // Affiche les résultats
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(finished).build();
    }

    private static Datacenter createDatacenter(CloudSim simulation) {
        List<Host> hostList = new ArrayList<>();

        // Crée un hôte avec 8 cœurs
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            peList.add(new PeSimple(1000, new PeProvisionerSimple()));
        }

        long ram = 20000; // MB
        long bw = 20000;  // Mb/s
        long storage = 10000000; // MB

        Host host = new HostSimple(ram, bw, storage, peList);
        host.setRamProvisioner(new ResourceProvisionerSimple());
        host.setBwProvisioner(new ResourceProvisionerSimple());
        host.setVmScheduler(new VmSchedulerTimeShared());

        hostList.add(host);

        return new DatacenterSimple(simulation, hostList);
    }
}
