# Wellread

Wellread will transfer your Instapaper queue to your Kindle and archive all of
the items afterward.

## Usage

First you'll need to setup Instapaper and Kindle according to the directions at
[http://www.instapaper.com/user/kindle]

Once that's done, you have a few options available as to how you want to run 
the script.

* lein run <username> <password>
* lein run --file <path>
* lein uberjar
  java -jar wellread-1.0.0-standalone.jar <username> <password>
  java -jar wellread-1.0.0-standalone.jar --file <path>

The file referenced by <path> above should be in the format of:

> username
> password


## License

Copyright (C) 2012 David Leatherman

Distributed under the Eclipse Public License, the same as Clojure.
