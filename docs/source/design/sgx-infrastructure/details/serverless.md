# Serverless architectures

In 2014 Amazon launched AWS Lambda, which they coined a "serverless architecture". It essentially creates an abstraction
layer which hides the infrastructure details. Users provide "lambdas", which are stateless functions that may invoke
other lambdas, access other AWS services etc. Because Lambdas are inherently stateless (any state they need must be
accessed through a service) they may be loaded and executed on demand. This is in contrast with microservices, which 
are inherently stateful. Internally AWS caches the lambda images and even caches JIT compiled/warmed up code in order 
to reduce latency. Furthermore the lambda invokation interface provides a convenient way to scale these lambdas: as the 
functions are statelesss AWS can spin up new VMs to push lambda functions to. The user simply pays for CPU usage, all 
the infrastructure pain is hidden by Amazon.

Google and Microsoft followed suit in a couple of years with Cloud Functions and Azure Functions.

This way of splitting hosting computation from a hosted restricted computation is not a new idea, examples are web
frameworks (web server vs application), MapReduce (Hadoop vs mappers/reducers), or even the cloud (hypervisors vs vms) 
and the operating system (kernel vs userspace). The common pattern is: the hosting layer hides some kind of complexity, 
imposes some restriction on the guest layer (and provides a simpler interface in turn), and transparently multiplexes 
a number of resources for them.

The relevant key features of serverless architectures are 1. on-demand scaling and 2. business logic independent of 
hosting logic.

# Serverless SGX?

How are Amazon Lambdas relevant to SGX? Enclaves exhibit very similar features to Lambdas: they are pieces of business 
logic completely independent of the hosting functionality. Not only that, enclaves treat hosts as adversaries! This 
provides a very clean separation of concerns which we can exploit.

If we could provide a similar infrastructure for enclaves as Amazon provides for Lambdas it would not only allow easy 
HA and scaling, it would also decouple the burden of maintaining the infrastructure from the enclave business logic. 
Furthermore our plan of using the JVM within enclaves also aligns with the optimizations Amazon implemented (e.g. 
keeping warmed up enclaves around). Optimizations like upgrading to local attestation also become orthogonal to 
enclave business logic. Enclave code can focus on the specific functionality at hand, everything else is taken care of.
