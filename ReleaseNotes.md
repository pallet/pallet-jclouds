# Release Notes

# 1.4.3

- Use only :image-id for template if specified
  Stop other information in the template from preventing a match.

- Update to pallet 0.7.2

# 1.4.2

- Make jclouds-all and jclouds-sshj test scoped

- Make os-version return nil instead of an empty string
  When the version is not available from jclouds, avoid returning an empty
  string.

- Add logic to detect sshj and jsch extensions
  jsch was being assumed.

- Add jclouds template to logging

- Ensure ssh-port returns a value

# 1.4.1

- Update to jclouds 1.4.2

# 1.4.0

- Remove jclouds-jsch as a direct dependency

- Add minimal test of blobstore, and fix compilation

- Log ssh testing features

- Update to jclouds 1.4.1

# 1.4.0-beta.1

Initial release for jclouds 1.4.0.

# 1.3.0-alpha.1

Initial release for jclouds 1.3.1.

# 1.2.0-alpha1

Initial release for jclouds 1.2.1.
