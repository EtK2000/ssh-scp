package com.etk2000.sealed.cloudprovider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.amazonaws.services.ec2.model.Tag;
import com.etk2000.sealed.config.Config;
import com.etk2000.sealed.config.Server;
import com.etk2000.sealed.platform.Platform;
import com.etk2000.sealed.service.ServiceCloud;
import com.etk2000.sealed.service.ServiceDynamicIP;
import com.etk2000.sealed.util.Util;
import com.google.cloud.Tuple;
import com.google.common.base.Predicates;

public class AWSCloudProvider implements ServiceDynamicIP, ServiceCloud {
	private static final Map<String, AWSCloudProvider> cache = new HashMap<>();

	public static AWSCloudProvider getFor(String id) throws IOException {
		final String key = id;
		AWSCloudProvider res = cache.get(key);
		if (res == null)
			cache.put(key, res = new AWSCloudProvider(id));
		return res;
	}

	private static CloudServer asCloudServer(Tuple<String, Instance> regionInstance) {
		Instance inst = regionInstance.y();
		List<String> names = getNames(inst);
		String nameOrID = names.size() > 0 ? names.get(0) : inst.getInstanceId();

		return new CloudServer(regionInstance.x(), nameOrID, inst.getInstanceType(), inst.getState().getName());
	}

	private static Tuple<Server, String> asServer(Tuple<String, Instance> regionInstance) {
		// check for a name we can use to identify the server
		for (String name : getNames(regionInstance.y())) {
			Server server = Config.getServer(name);
			if (server != null)
				return Tuple.of(server, regionInstance.y().getPublicIpAddress());
		}

		return null;
	}

	private static void checkRegion(String region, List<Tuple<String, Instance>> out, AmazonEC2 ec2) {
		List<Tuple<String, Instance>> found = new ArrayList<>();

		DescribeInstancesRequest request = new DescribeInstancesRequest();
		for (;;) {
			DescribeInstancesResult response = ec2.describeInstances(request);
			response.getReservations().forEach(r -> found.addAll(r.getInstances().stream().map(instance -> Tuple.of(region, instance)).collect(Collectors.toList())));
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

	private static List<String> getNames(Instance instance) {
		return instance.getTags().stream().filter(t -> t.getKey().equals("Name")).map(Tag::getValue).collect(Collectors.toList());
	}

	private final AWSCredentialsProvider provider;

	private AWSCloudProvider(String id) throws IOException {
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

	private List<Tuple<String, Instance>> getAllServers() {
		DescribeRegionsResult result = ec2(Regions.DEFAULT_REGION.getName()).describeRegions();
		List<Thread> ts = new ArrayList<>(result.getRegions().size());
		List<Tuple<String, Instance>> res = new ArrayList<>();

		// summon a lookup thread for each region
		for (Region region : result.getRegions()) {
			Thread t = new Thread(() -> checkRegion(region.getRegionName(), res, ec2(region.getRegionName())));
			t.start();
			ts.add(t);
		}

		// wait for lookup to complete
		ts.forEach(t -> Util.ignoreInterrupt(t::join));

		return res;
	}

	@Override
	public List<Tuple<Server, String>> fetchIPs() {
		return getAllServers().stream().map(AWSCloudProvider::asServer).filter(Predicates.notNull()).collect(Collectors.toList());
	}

	@Override
	public List<CloudServer> fetchServers() {
		return getAllServers().stream().map(AWSCloudProvider::asCloudServer).collect(Collectors.toList());
	}
	@Override
	public String name() {
		return "AWS";
	}
}