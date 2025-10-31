/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2021 Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples.autoscaling;

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.distributions.ContinuousDistribution;
import org.cloudbus.cloudsim.distributions.UniformDistr;
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
import org.cloudsimplus.autoscaling.HorizontalVmScaling;
import org.cloudsimplus.autoscaling.HorizontalVmScalingSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static java.util.Comparator.comparingDouble;

/**
 * LoadBalancerByHorizontalVmScalingExample modifié pour :
 * - 1 hôte avec 8 PEs, RAM 20000, stockage 10000000, BW 20000
 * - 2 VMs avec 2 PEs, RAM 1000, BW 1000, taille 10000
 * - Horizontal scaling dès que CPU > 70%
 * - 20 cloudlets générés toutes les 4s jusqu'à un total de 80 cloudlets
 * - Longueurs de cloudlets de 10000 à 200000 par pas de 10000
 */
public class LoadBalancerByHorizontalVmScalingExample {
    private static final int SCHEDULING_INTERVAL = 1;
    private static final int CLOUDLETS_CREATION_INTERVAL = 4;
    private static final int HOSTS = 1;
    private static final int HOST_PES = 8;
    private static final int VMS = 2;
    private static final int CLOUDLETS_BATCH = 20;//modifier a 20
    private static final int TOTAL_CLOUDLETS = 80;

    private final CloudSim simulation;
    private final Datacenter dc0;
    private final DatacenterBroker broker0;
    private final List<Host> hostList;
    private final List<Vm> vmList;
    private final List<Cloudlet> cloudletList;

    private final List<Long> cloudletLengths = new ArrayList<>();
    private final ContinuousDistribution rand;

    private int createdCloudlets;
    private int createsVms;

    public static void main(String[] args) {
        new LoadBalancerByHorizontalVmScalingExample();
    }

    private LoadBalancerByHorizontalVmScalingExample() {
        final long seed = 1;
        rand = new UniformDistr(0, 1, seed); // pas utilisé mais conservé
        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(TOTAL_CLOUDLETS);

        simulation = new CloudSim();
        simulation.addOnClockTickListener(this::createNewCloudlets);

        dc0 = createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);
        broker0.setVmDestructionDelay(10.0);

        vmList.addAll(createListOfScalableVms(VMS));

        // remplir les longueurs de cloudlets de 10000 à 200000 par pas de 10000
        for (long l = 10000; l <= 200000; l += 10000) cloudletLengths.add(l);

        createCloudletList(); // crée les premiers cloudlets (facultatif)

        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        printSimulationResults();
    }

    private void printSimulationResults() {
        final List<Cloudlet> finishedCloudlets = broker0.getCloudletFinishedList();
        final Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        final Comparator<Cloudlet> sortByStartTime = comparingDouble(Cloudlet::getExecStartTime);
        finishedCloudlets.sort(sortByVmId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(finishedCloudlets).build();
    }

    private void createCloudletList() {
        for (int i = 0; i < Math.min(CLOUDLETS_BATCH, TOTAL_CLOUDLETS); i++) {
            cloudletList.add(createCloudlet());
        }
    }

    private void createNewCloudlets(final EventInfo info) {
        final long time = (long) info.getTime();
        if (time % CLOUDLETS_CREATION_INTERVAL == 0 && createdCloudlets < TOTAL_CLOUDLETS) {
            System.out.printf("\t#Creating %d Cloudlets at time %d.%n", CLOUDLETS_BATCH, time);
            final List<Cloudlet> newCloudlets = new ArrayList<>(CLOUDLETS_BATCH);
            for (int i = 0; i < CLOUDLETS_BATCH; i++) {
                if (createdCloudlets >= TOTAL_CLOUDLETS) break;
                final Cloudlet cloudlet = createCloudlet();
                cloudletList.add(cloudlet);
                newCloudlets.add(cloudlet);
            }
            broker0.submitCloudletList(newCloudlets);
        }
    }

    private Datacenter createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }
        return new DatacenterSimple(simulation, hostList).setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        final List<Pe> peList = new ArrayList<>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000, new PeProvisionerSimple()));
        }
        return new HostSimple(20000, 20000, 10000000, peList)
                .setRamProvisioner(new ResourceProvisionerSimple())
                .setBwProvisioner(new ResourceProvisionerSimple())
                .setVmScheduler(new VmSchedulerTimeShared());
    }

    private List<Vm> createListOfScalableVms(final int vmsNumber) {
        final List<Vm> newList = new ArrayList<>(vmsNumber);
        for (int i = 0; i < vmsNumber; i++) {
            final Vm vm = createVm();
            createHorizontalVmScaling(vm);
            newList.add(vm);
        }
        return newList;
    }

    private void createHorizontalVmScaling(final Vm vm) {
        final HorizontalVmScaling horizontalScaling = new HorizontalVmScalingSimple();
        horizontalScaling
                .setVmSupplier(this::createVm)
                .setOverloadPredicate(this::isVmOverloaded);
        vm.setHorizontalScaling(horizontalScaling);
    }

    private boolean isVmOverloaded(final Vm vm) {
        return vm.getCpuPercentUtilization() > 0.7;
    }

    private Vm createVm() {
        final int id = createsVms++;
        return new VmSimple(id, 1000, 2)
                .setRam(1000).setBw(1000).setSize(10000)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    private Cloudlet createCloudlet() {
        final int id = createdCloudlets++;
        final long length = cloudletLengths.get(id % cloudletLengths.size());
        return new CloudletSimple(id, length, 2)
                .setFileSize(1024)
                .setOutputSize(1024)
                .setUtilizationModel(new UtilizationModelFull());
    }
}

