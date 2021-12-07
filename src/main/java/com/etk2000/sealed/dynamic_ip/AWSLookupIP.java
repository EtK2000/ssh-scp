package com.etk2000.sealed.dynamic_ip;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeRegionsResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.util.Util;
import com.google.cloud.Tuple;

public class AWSLookupIP extends DynamicIP {
	private static void checkRegion(List<Instance> out, AmazonEC2 ec2) {
		List<Instance> found = new ArrayList<>();

		DescribeInstancesRequest request = new DescribeInstancesRequest();
		for (;;) {
			DescribeInstancesResult response = ec2.describeInstances(request);
			for (Reservation reservation : response.getReservations())
				found.addAll(reservation.getInstances());
			if (response.getNextToken() == null)
				break;

			request.setNextToken(response.getNextToken());
		}

		if (found.size() > 0) {
			synchronized (out) {
				out.addAll(found);
			}
		}
	}

	private final AWSCredentialsProvider provider;

	AWSLookupIP(String id) throws IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(new File(Platform.dirKeys(), id + ".csv")))) {
			br.readLine();// skip header line
			String[] fragments = br.readLine().split(",", 2);
			if (fragments.length != 2)
				throw new IOException("invalid AWS access CSV");
			provider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(fragments[0], fragments[1]));
		}
	}

	private AmazonEC2 ec2(String region) {
		return AmazonEC2ClientBuilder.standard().withCredentials(provider).withRegion(region).build();
	}

	@Override
	public List<Tuple<Server, String>> fetch() {
		DescribeRegionsResult result = ec2(Regions.DEFAULT_REGION.getName()).describeRegions();
		List<Thread> ts = new ArrayList<>(result.getRegions().size());
		List<Instance> instances = new ArrayList<>();

		// summon a lookup thread for each region
		for (Region region : result.getRegions()) {
			Thread t = new Thread(() -> checkRegion(instances, ec2(region.getRegionName())));
			t.start();
			ts.add(t);
		}

		// wait for lookup to complete
		ts.forEach(t -> Util.ignoreInterrupt(t::join));

		// update servers that have associated instances
		List<Tuple<Server, String>> res = new ArrayList<>();
		for (Instance instance : instances) {
			
			// check for a name we can use to identify the server
			String name = null;
			for (Tag t : instance.getTags()) {
				if (t.getKey().equals("Name")) {
					name = t.getValue();
					break;
				}
			}
			if (name != null) {

				// ensure we have a correlating server
				Server server = Config.getServer(name);
				if (server != null)
					res.add(Tuple.of(server, instance.getPublicIpAddress()));
			}
		}
		
		return res;
	}
}