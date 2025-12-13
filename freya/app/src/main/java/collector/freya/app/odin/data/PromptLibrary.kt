package collector.freya.app.odin.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Expand
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Grid3x3
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Groups3
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.RollerSkating
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.ScatterPlot
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TagFaces
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Waves

object PromptLibrary {
    val prompts = listOf(
        // --- Coding & Technical ---
        "Write a Python script to automate organizing files in a folder." to Icons.Outlined.Folder,
        "Explain the key differences between TCP and UDP protocols." to Icons.Outlined.SwapHoriz,
        "Help me debug a Kotlin Flow collection issue in my UI." to Icons.Outlined.BugReport,
        "Create a robust RegEx pattern to validate email addresses." to Icons.Outlined.AlternateEmail,
        "How can I optimize this SQL query for millions of rows?" to Icons.Outlined.Storage,
        "Explain Dependency Injection to a five-year-old using analogies." to Icons.Outlined.Extension,
        "Write a Bash script to backup a specific directory every night." to Icons.Outlined.Save,
        "What are the top best practices for designing a RESTful API?" to Icons.Outlined.Api,
        "Generate a large random JSON dataset for unit testing." to Icons.Outlined.DataObject,
        "Refactor this imperative code block into a functional style." to Icons.Outlined.Build,
        "Explain Big O notation and time complexity with code examples." to Icons.Outlined.Timeline,
        "How does garbage collection actually work in the Java JVM?" to Icons.Outlined.DeleteSweep,
        "Write a React functional component for a secure login form." to Icons.Outlined.Login,
        "Explain the relationship between Kubernetes Pods, Nodes, and Clusters." to Icons.Outlined.Apps,
        "What are the benefits of an Event-Driven Architecture?" to Icons.Outlined.FlashOn,
        "How does distributed caching work in a microservices environment?" to Icons.Outlined.Layers,
        "Implement a rate limiting algorithm for an API endpoint." to Icons.Outlined.Timer,
        "Visualize how async/await works under the hood." to Icons.Outlined.PendingActions,
        "Compare gRPC and REST for internal service communication." to Icons.Outlined.CompareArrows,
        "Explain the step-by-step process of a DNS lookup." to Icons.Outlined.Public,
        "Walk me through the OAuth2 authorization code flow." to Icons.Outlined.VpnKey,
        "How do Content Delivery Networks (CDNs) speed up websites?" to Icons.Outlined.CloudDone,
        "Write a simple WebSocket client and server in Node.js." to Icons.Outlined.Sync,
        "Design the high-level architecture for a scalable chat app." to Icons.Outlined.Chat,
        "What are the different strategies for load balancing traffic?" to Icons.Outlined.AutoMode,
        "Pros and cons of Monolithic vs. Microservices architectures." to Icons.Outlined.ViewModule,
        "How does the HTTPS handshake establish a secure connection?" to Icons.Outlined.Lock,
        "Explain how collaborative filtering works in recommendation systems." to Icons.Outlined.Star,
        "Demonstrate how an SQL Injection attack happens and how to fix it." to Icons.Outlined.Dangerous,
        "What is Cross-Site Scripting (XSS) and how do I prevent it?" to Icons.Outlined.Report,
        "Explain what causes a deadlock in multithreaded applications." to Icons.Outlined.Cancel,
        "What do the ACID properties ensure in database transactions?" to Icons.Outlined.FactCheck,
        "Explain the MapReduce programming model simply." to Icons.Outlined.GridView,
        "How do artificial neural networks simulate the human brain?" to Icons.Outlined.Psychology,
        "What is the concept of entropy in information theory?" to Icons.Outlined.ScatterPlot,
        "Explain homomorphic encryption and why it matters." to Icons.Outlined.Lock,
        "Explain the fundamental technology behind Blockchain." to Icons.Outlined.Link,
        "Explain the CAP Theorem (Consistency, Availability, Partition Tolerance)." to Icons.Outlined.Category,
        "How does the Publish/Subscribe messaging pattern work?" to Icons.Outlined.Speaker,
        "Why is cache invalidation considered one of the hardest problems?" to Icons.Outlined.Delete,
        "Explain how Docker image layering optimizes storage." to Icons.Outlined.LayersClear,
        "What is backpropagation in the context of machine learning?" to Icons.Outlined.Loop,
        "Explain database sharding and when it becomes necessary." to Icons.Outlined.Grid3x3,
        "What is the CQRS pattern and when should I use it?" to Icons.Outlined.Compare,
        "What is the difference between a Mutex and a Semaphore?" to Icons.Outlined.LockClock,
        "Compare static linking versus dynamic linking in C++." to Icons.Outlined.LinkOff,
        "Explain how the internet works to a complete beginner." to Icons.Outlined.Public,
        "Virtual Machines vs. Containers: What's the difference?" to Icons.Outlined.DeveloperBoard,
        "Write a command-line interface (CLI) tool using Go." to Icons.Outlined.Terminal,

        // --- Creative & Writing ---
        "Compose a haiku about a robot discovering it has feelings." to Icons.Outlined.Favorite,
        "Draft a polite email declining a job offer but keeping the door open." to Icons.Outlined.Mail,
        "Brainstorm five unique names for a cozy, book-themed coffee shop." to Icons.Outlined.LocalCafe,
        "Write the opening paragraph for a noir mystery novel set in 1920." to Icons.Outlined.MenuBook,
        "Create a detailed 7-day travel itinerary for a first trip to Japan." to Icons.Outlined.FlightTakeoff,
        "Suggest ten catchy headlines for a blog post about AI ethics." to Icons.Outlined.Tag,
        "Write a melancholic free-verse poem about the ocean at night." to Icons.Outlined.Waves,
        "Write a short, soothing bedtime story for a restless child." to Icons.Outlined.Bedtime,
        "Invent a concept album about space travel and list the track titles." to Icons.Outlined.Album,
        "Script a 30-second commercial for a device that pauses time." to Icons.Outlined.Videocam,
        "Describe a neon-lit cyberpunk city during a heavy rainstorm." to Icons.Outlined.LocationCity,
        "Write a stand-up comedy bit about the struggles of modern dating." to Icons.Outlined.Mic,
        "Describe the atmosphere of an abandoned, haunted library." to Icons.Outlined.LocalLibrary,
        "Write a dramatic monologue for a villain justifying their actions." to Icons.Outlined.TheaterComedy,
        "Write a complete sci-fi story in exactly 100 words." to Icons.Outlined.Rocket,
        "Describe a surreal dream sequence with vivid imagery." to Icons.Outlined.NightsStay,

        // --- Academic & Learning ---
        "Explain the phenomenon of quantum entanglement to a layperson." to Icons.Outlined.AutoAwesome,
        "Summarize the primary social and economic causes of the French Revolution." to Icons.Outlined.History,
        "What is the difference between nuclear fusion and fission?" to Icons.Outlined.LocalFireDepartment,
        "Explain the physics behind how heavy airplanes manage to fly." to Icons.Outlined.Flight,
        "Walk me through the process of photosynthesis in plants." to Icons.Outlined.Forest,
        "How does the human immune system distinguish self from non-self?" to Icons.Outlined.Healing,
        "Explain Einstein's Theory of Relativity using a train analogy." to Icons.Outlined.AutoAwesome,
        "Explain the Schrödinger's Cat thought experiment." to Icons.Outlined.Pets,
        "What is the scientific evidence supporting climate change?" to Icons.Outlined.Eco,
        "Explain why time passes slower near massive objects (Time Dilation)." to Icons.Outlined.HourglassTop,
        "What is gravity, according to General Relativity?" to Icons.Outlined.Expand,
        "What do we know about Dark Matter and Dark Energy?" to Icons.Outlined.Star,
        "Explain how Plate Tectonics shape the Earth's surface." to Icons.Outlined.Landscape,
        "Explain the mechanism of natural selection in evolution." to Icons.Outlined.Pets,

        // --- Productivity & Soft Skills ---
        "Give me tips to improve my public speaking and reduce anxiety." to Icons.Outlined.RecordVoiceOver,
        "Create a professional template for a freelance project proposal." to Icons.Outlined.Description,
        "What are some impactful questions to ask the interviewer?" to Icons.Outlined.QuestionAnswer,
        "What are the best strategies for negotiating a higher salary?" to Icons.Outlined.AttachMoney,
        "Create a realistic study schedule for final exams." to Icons.Outlined.Event,
        "Recommend five must-read books for personal growth and mindset." to Icons.Outlined.MenuBook,
        "How should I handle a disagreement with a difficult coworker?" to Icons.Outlined.SentimentDissatisfied,
        "Draft a compelling 'About Me' summary for my LinkedIn profile." to Icons.Outlined.Badge,
        "What are some psychological tricks to stop procrastinating?" to Icons.Outlined.Alarm,
        "Share some effective time management techniques like Pomodoro." to Icons.Outlined.Schedule,
        "What are the early warning signs of burnout and how to prevent it?" to Icons.Outlined.SelfImprovement,
        "How do I transition from an individual contributor to a team lead?" to Icons.Outlined.Groups,
        "How do I run an effective meeting that doesn't waste time?" to Icons.Outlined.Groups3,
        "How do I prioritize tasks using the Eisenhower Matrix?" to Icons.Outlined.Sort,

        // --- Fun & Trivia ---
        "Tell me a joke that only a software engineer would understand." to Icons.Outlined.TagFaces,
        "Tell me three mind-blowing facts about octopuses." to Icons.Outlined.BubbleChart,
        "If you could have one superhero power, what would it be and why?" to Icons.Outlined.FlashOn,
        "Give me a hard trivia question about 90s action movies." to Icons.Outlined.Movie,
        "Rank the Star Wars movies from best to worst with reasons." to Icons.Outlined.Stars,
        "Tell me a riddle that is difficult to solve." to Icons.Outlined.Help,
        "Who would win in a fight: a bear or a shark?" to Icons.Outlined.Dangerous,
        "Invent a new holiday involving cheese and explain the traditions." to Icons.Outlined.Celebration,
        "Describe the ingredients for the absolute perfect sandwich." to Icons.Outlined.LunchDining,
        "Invent a concept for a board game involving time travel." to Icons.Outlined.Extension,
        "Pitch an idea for a thrill ride at a theme park." to Icons.Outlined.RollerSkating,
        "Invent a fake law of physics and describe how it works." to Icons.Outlined.Gavel,
        "Predict what humanity will look like in the year 3000." to Icons.Outlined.Public
    )
}