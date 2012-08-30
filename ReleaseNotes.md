# Release Notes

# 1.5.0-alpha.1

- Update to jclouds 1.5.0 beta.11

- Make jclouds-all and jclouds-sshj test scoped

- Make os-version return nil instead of an empty string
  When the version is not available from jclouds, avoid returning an empty
  string.

- Force the use of a run script
  This ensures that ssh is useable when the nodes are returned from jclouds

- Add test for image-user and fix

- Add support for pallet 0.8.x

- Add logic to detect sshj and jsch extensions
  jsch was being assumed.

- Add jclouds template to logging

- Ensure ssh-port returns a value

- Implement getStatus on ComputeMetadataIncludingStatus
  It was being implemented on NodeMetadata

- Add getStatus method to JcloudsNode

