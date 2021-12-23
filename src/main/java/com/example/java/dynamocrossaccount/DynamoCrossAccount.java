package com.example.java.dynamocrossaccount;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.StsException;

public class DynamoCrossAccount {

	public static void main(String[] args) {
		/*Change this variables according to the role created*/
		String roleARN = "arn:aws:iam::<ACCOUNT_B>:role/<ROLE_NAME>";
		String roleSessionName = "DynamoCrossAccount";
		Region region = Region.US_EAST_1;

		StsClient stsClient = StsClient.builder().region(region).build();
		Credentials myCreds = assumeGivenRole(stsClient, roleARN, roleSessionName);

		AwsSessionCredentials awsCreds = AwsSessionCredentials.create(myCreds.accessKeyId(), myCreds.secretAccessKey(),
				myCreds.sessionToken());

		DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
				.credentialsProvider(StaticCredentialsProvider.create(awsCreds)).region(region).build();

		listAllTables(dynamoDbClient);

		stsClient.close();
	}

	public static Credentials assumeGivenRole(StsClient stsClient, String roleArn, String roleSessionName) {
		Credentials myCreds = null;

		try {
			AssumeRoleRequest roleRequest = AssumeRoleRequest.builder().roleArn(roleArn)
					.roleSessionName(roleSessionName).build();

			AssumeRoleResponse roleResponse = stsClient.assumeRole(roleRequest);
			myCreds = roleResponse.credentials();

			// Display the time when the temp creds expire
			Instant exTime = myCreds.expiration();
			String tokenInfo = myCreds.sessionToken();

			// Convert the Instant to readable date
			DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.US)
					.withZone(ZoneId.systemDefault());

			formatter.format(exTime);
			System.out.println("The token " + tokenInfo + "  expires on " + exTime);

		} catch (StsException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		return myCreds;
	}

	public static void listAllTables(DynamoDbClient ddb) {

		boolean moreTables = true;
		String lastName = null;

		while (moreTables) {
			try {
				ListTablesResponse response = null;
				if (lastName == null) {
					ListTablesRequest request = ListTablesRequest.builder().limit(10).build();

					response = ddb.listTables(request);
				} else {
					ListTablesRequest request = ListTablesRequest.builder().exclusiveStartTableName(lastName).build();
					response = ddb.listTables(request);
				}

				List<String> tableNames = response.tableNames();

				if (tableNames.size() > 0) {
					for (String curName : tableNames) {
						System.out.format("* %s\n", curName);
					}
				} else {
					System.out.println("No tables found!");
					System.exit(0);
				}

				lastName = response.lastEvaluatedTableName();
				if (lastName == null) {
					moreTables = false;
				}
			} catch (DynamoDbException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
		}
		System.out.println("\nDone!");
	}

}
