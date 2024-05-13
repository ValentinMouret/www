# A/B testing a high load, low latency system at PubNative

## PubNative
### Quick description
At [PubNative](https://www.pubnative.net/?utm_source=medium&utm_medium=blogpost&utm_campaign=ab_testing), we run an ad exchange.
When you use a mobile application, (e.g., the TV Smiles app), or a website, and there is some form of advertising, it probably came from an ad exchange like ours (if not ours).

The app has a piece of code (typically an SDK), that sends an ad request to an ad server with information about the ad opportunity.
This information would usually describe the dimensions of the ad space, the format, the device environment, the platform (Android or iOS), geographical data, contextual information, and more.

This ad server then sends an ad request to a network of actors than can be:
- [SSPs](https://smartyads.com/glossary/ssp-definition),
- [Exchanges](https://smartyads.com/glossary/ad-exchange-definition),
- [DSPs](https://smartyads.com/glossary/dsp-definition)

Between each actor, there is a deferred auction. DSPs are connected to advertisers that may bid some amount of money to win the series of auctions to display their ad.
And that happens almost every time you see an ad in a matter of milliseconds!

## Data science at PubNative 👨‍🔬👩‍🔬
### Quick description
As data scientists, we automate decision making. To do so, we want to make as many experiments as we can; iterating fast, discarding what’s not working, and proceeding with what is.

This could be, for instance, to see how some supply source suits our demand partners. It has clear business benefits and could seldom be done by humans, given the number of requests we receive and their diversity.

We can build and test strategies offline as much as we want, there is a time when we must immerse our systems into production traffic and evaluate them. We do this prospectively with A/B tests.
We split the population into uniquely identified, mutually exclusive groups that may receive different treatments, leave one group to be the control group, and assess whether or not a treatment makes a difference according to some statistics.

Ideally, we want to be testing as many versions of A/B tests as possible concurrently and iterate quickly.

### Constraints
Until now, nothing is out of the ordinary for a data science workflow. However, we have to face three significant constraints:
- 📈 high volume: we see around 35B ad requests per day (the input of our system), which is about 400k requests per second, in over 250 countries across the globe
- ⏱ low latency: we need to stay below 200ms in I/O (including back-and-forth communications with our DSPs)
- 🧙‍♂️ headcount: we are a rather small tech team, split into backend, data, infrastructure, mobile, and data science, and have to stay efficient

### Prior setup
A couple of years ago, the back-end team rewrote the ad server in Go (coming from Ruby), allowing it to scale better. We now require fewer resources for the same amount of incoming requests, and the performance is better.

We now have a very efficient ad server that our engineers keep on improving every day! But it came at the expense of some flexibility. Every time we wanted to make an A/B test, we had to write the logic for each group on the ad server.
This means that:
- they would not impact much the performance
- they required a back-end engineer or a data scientist with the knowledge of Go
- they shared the release cycles and deployment procedures of the ad server

This setup was respecting our first two constraints but was limiting the speed of experiments as well as requiring a back-end engineer every time we needed changes.

It was apparent then that we needed to decouple the A/B testing from the ad server as much as possible while satisfying our constraints.

## Building A/B test APIs
### Design
To decouple our systems, we need to create a microservice. In the parts of the ad server’s code we want to A/B test; we make it call the API with information about the current auction and expect a response.

### Example
Let’s say we want to know to which of our DSPs we should ask to participate in an auction (note: each request costs us money in data transfer costs). It makes sense that all DSPs are not interested in the same traffic, so we need to make decisions there.
At the same time, it’s not a trivial decision to make, so we need to test different strategies, so lets A/B test!

Let’s say we want to test some strategy on 1% of the traffic.
When the ad server needs to decide on which DSPs it should forward the auction to, it has a 1% chance to call our API first.
It sends a request with information about the auction, available DSPs, and the test group ID allowing us to identify the treatment we should apply, unbeknownst to the ad server.

The API receives it, maps the ID to a strategy to apply, applies it, and returns a list of suggestions.
_Et voilà!_
At any point, we can tweak the logic on the API and deploy it without the ad server knowing anything about it and without requiring a back-end engineer’s time.

### Caching
1% of 35B requests is still a lot of requests (duh.).
Calling our API every time would not be tractable, so we have to cache responses on the ad server’s instances.
This constrains the features we can use because they have to fit in a hash map without blowing it up.

### Implementation
Python is not known for its performance, so we had to choose another language.
Go was too low level for what we wanted to do and is not usually found in a data scientist’s toolbelt, so we settled on Scala.
It has a robust and well supported high-concurrency HTTP library in Akka, and our data team is already using it so they could lay a hand.

We already had an admin UI made with Rails for our account managers to, well, manage accounts, and we added pages to manage A/B tests.
On modifications of the weights of the tests, the ad server pulls the new values and adapts its splits accordingly.

## Conclusions
After some time using this setup, we can state it’s a significant improvement to the prior. It means less overhead for the back-end engineers that just have to implement the best performing logic

### Improvements
- Fewer backend-engineer hours are needed to manage A/B tests.
- Release cycles and deployment procedures are independent of that of the ad server.
- The testing velocity and diversity is increased.
### Limits
- Scala may still not be super straightforward for any data-scientist,
- Akka can be obscure at times even if it may appear simple to use,
- it adds responsibilities to the data-science team, such as monitoring the health of the microservices.
