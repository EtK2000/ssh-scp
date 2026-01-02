package com.etk2000.ssh_scp.cloudprovider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeRegionsResult;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ec2.model.Tag;
import com.etk2000.ssh_scp.api.ApiCloud;
import com.etk2000.ssh_scp.api.ApiDynamicIP;
import com.etk2000.ssh_scp.api.MissingFieldsException;
import com.etk2000.ssh_scp.api.ServiceDynamicCloud;
import com.etk2000.ssh_scp.config.Config;
import com.etk2000.ssh_scp.config.Server;
import com.etk2000.ssh_scp.platform.Platform;
import com.etk2000.ssh_scp.util.Util;
import com.google.cloud.Tuple;
import com.google.common.base.Predicates;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ServiceAWS implements ServiceDynamicCloud {
	private static final Map<String, String> AMI_USERNAME_LOOKUP = new HashMap<>();

	private static Tuple<Server, String> asServer(Tuple<String, Instance> regionInstance) {
		return lookupIP(regionInstance.y());
	}

	private static void checkRegion(String region, List<Tuple<String, Instance>> out, AmazonEC2 ec2) {
		List<Tuple<String, Instance>> found = new ArrayList<>();

		DescribeInstancesRequest request = new DescribeInstancesRequest();
		for (;;) {
			DescribeInstancesResult response = ec2.describeInstances(request);
			response.getReservations()
					.forEach(r -> found.addAll(r.getInstances().stream().map(instance -> Tuple.of(region, instance)).collect(Collectors.toList())));
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

	// check for a name we can use to identify the server
	private static Tuple<Server, String> lookupIP(Instance inst) {
		for (String name : getNames(inst)) {
			Server server = Config.getServer(name);
			if (server != null)
				return Tuple.of(server, inst.getPublicIpAddress());
		}
		return null;
	}

	public static ServiceAWS read(JsonReader jr) throws IOException {
		String id = null;

		jr.beginObject();
		{
			while (jr.hasNext()) {
				switch (jr.nextName()) {
					case "id":
						id = jr.nextString();
						break;
					default:
						jr.skipValue();
						break;
				}
			}
		}
		jr.endObject();
		if (id == null)
			throw new MissingFieldsException();

		// FIXME: prevent duplicates via key, for example:
		// final String key = id;
		ServiceAWS res = new ServiceAWS(id);
		ApiCloud.register(res.name(), res);
		ApiDynamicIP.register(res.name(), res);
		return res;
	}

	private final AWSCredentialsProvider provider;
	private final String id;

	public ServiceAWS(AWSCredentials credentials) {
		this.id = credentials.getAWSAccessKeyId();
		provider = new AWSStaticCredentialsProvider(credentials);
	}

	private ServiceAWS(String id) throws IOException {
		this.id = id;

		try (BufferedReader br = new BufferedReader(new FileReader(new File(Platform.dirKeys(), id + ".csv")))) {
			br.readLine();// skip header line
			String[] fragments = br.readLine().split(",", 2);
			if (fragments.length != 2)
				throw new IOException("invalid AWS access CSV");
			provider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(fragments[0], fragments[1]));
		}
	}

	private CloudServer asCloudServer(Tuple<String, Instance> regionInstance) {
		Instance inst = regionInstance.y();
		List<String> names = getNames(inst);
		String nameOrID = names.size() > 0 ? names.get(0) : inst.getInstanceId();
		Tuple<Server, String> ip = lookupIP(inst);
		String ami = inst.getImageId();

		String user = AMI_USERNAME_LOOKUP.get(ami);

		// TODO: look into caching to speed up
		if (user == null) {
			List<Image> images = ec2(regionInstance.x()).describeImages(new DescribeImagesRequest().withExecutableUsers("all").withImageIds(ami)).getImages();

			if (images.size() == 1) {
				String description = images.get(0).getDescription().toLowerCase();
				if (description.contains("amazon linux"))
					user = "ec2-user";

				// FIXME:
				// https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connection-prereqs.html
				/**
				 * 
				 * For a CentOS AMI, the user name is centos or ec2-user.
				 * 
				 * For a Debian AMI, the user name is admin.
				 * 
				 * For a Fedora AMI, the user name is fedora or ec2-user.
				 * 
				 * For a RHEL AMI, the user name is ec2-user or root.
				 * 
				 * For a SUSE AMI, the user name is ec2-user or root.
				 * 
				 * For an Ubuntu AMI, the user name is ubuntu.
				 * 
				 * For an Oracle AMI, the user name is ec2-user.
				 * 
				 * For a Bitnami AMI, the user name is bitnami.
				 * 
				 * Otherwise, check with the AMI provider.
				 */

				if (user != null) {
					AMI_USERNAME_LOOKUP.put(ami, user);
					System.out.println("[AMIlookup] username for '" + ami + "' is '" + user + '\'');
				}
			}
		}

		// FIXME: fetch fingerprint
		return new CloudServer(ip != null ? ip.y() : null, regionInstance.x(), nameOrID, inst.getInstanceType(), inst.getState().getName(), inst.getKeyName(),
				user);
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
		return getAllServers().stream().map(ServiceAWS::asServer).filter(Predicates.notNull()).collect(Collectors.toList());
	}

	@Override
	public List<CloudServer> fetchServers(boolean fetchIPs) {
		// FIXME: implement
		return getAllServers().stream().map(this::asCloudServer).collect(Collectors.toList());
	}

	@Override
	public String name() {
		return "AWS::" + id;
	}

	@Override
	public void write(JsonWriter jw) throws IOException {
		jw.name(name().toLowerCase()).beginObject();
		{
			jw.name("id").value(id);
		}
		jw.endObject();
	}
}