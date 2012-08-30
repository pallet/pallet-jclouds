# Release Notes

# 1.3.0

- Make jclouds-all and jclouds-sshj test scoped

- Make os-version return nil instead of an empty string
  When the version is not available from jclouds, avoid returning an empty
  string.

- Add logic to detect sshj and jsch extensions
  jsch was being assumed.

- Add jclouds template to logging

- Ensure ssh-port returns a value

# 1.3.0-beta.1

- Update to pallet 0.7.0-beta.2

# 1.3.0-alpha.1

Initial release for jclouds 1.3.1.

# 1.2.0-alpha1

Initial release for jclouds 1.2.1.
