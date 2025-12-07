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
        "Write a Python script to automate file organization." to Icons.Outlined.Folder,
        "Explain the difference between TCP and UDP." to Icons.Outlined.SwapHoriz,
        "Debug this Kotlin flow collection issue." to Icons.Outlined.BugReport,
        "Create a regex to validate email addresses." to Icons.Outlined.AlternateEmail,
        "How do I optimize a SQL query for large datasets?" to Icons.Outlined.Storage,
        "Explain Dependency Injection to a 5-year-old." to Icons.Outlined.Extension,
        "Write a bash script to backup a directory." to Icons.Outlined.Save,
        "What are the best practices for REST API design?" to Icons.Outlined.Api,
        "Generate a random JSON dataset for testing." to Icons.Outlined.DataObject,
        "Refactor this function to be more functional style." to Icons.Outlined.Build,
        "Explain Big O notation with examples." to Icons.Outlined.Timeline,
        "How does garbage collection work in Java?" to Icons.Outlined.DeleteSweep,
        "Write a React component for a login form." to Icons.Outlined.Login,
        "Explain Kubernetes pods, nodes, and clusters." to Icons.Outlined.Apps,
        "Explain event-driven architecture." to Icons.Outlined.FlashOn,
        "Distributed caching explained." to Icons.Outlined.Layers,
        "Implement rate limiting in an API." to Icons.Outlined.Timer,
        "Explain async/await visually." to Icons.Outlined.PendingActions,
        "Compare gRPC and REST." to Icons.Outlined.CompareArrows,
        "How does DNS work?" to Icons.Outlined.Public,
        "What is OAuth2?" to Icons.Outlined.VpnKey,
        "How do CDNs work?" to Icons.Outlined.CloudDone,
        "Write a WebSocket client/server." to Icons.Outlined.Sync,
        "Design a scalable chat system." to Icons.Outlined.Chat,
        "Explain load balancing strategies." to Icons.Outlined.AutoMode,
        "Explain microservices vs monolith." to Icons.Outlined.ViewModule,
        "Explain HTTPS." to Icons.Outlined.Lock,
        "Explain recommendation systems." to Icons.Outlined.Star,
        "Explain SQL injection." to Icons.Outlined.Dangerous,
        "Explain cross-site scripting." to Icons.Outlined.Report,
        "Explain deadlocks in multithreading." to Icons.Outlined.Cancel,
        "Explain ACID transactions." to Icons.Outlined.FactCheck,
        "Explain MapReduce." to Icons.Outlined.GridView,
        "Explain neural networks." to Icons.Outlined.Psychology,
        "Explain entropy in computing." to Icons.Outlined.ScatterPlot,
        "Explain homomorphic encryption." to Icons.Outlined.Lock,
        "Explain blockchain simply." to Icons.Outlined.Link,
        "Explain CAP theorem." to Icons.Outlined.Category,
        "Explain pub/sub pattern." to Icons.Outlined.Speaker,
        "Explain cache invalidation." to Icons.Outlined.Delete,
        "Explain Docker image layers." to Icons.Outlined.LayersClear,
        "Explain backpropagation." to Icons.Outlined.Loop,
        "Sharding a database explained." to Icons.Outlined.Grid3x3,
        "Explain CQRS." to Icons.Outlined.Compare,
        "Mutex vs semaphore." to Icons.Outlined.LockClock,
        "Static vs dynamic linking." to Icons.Outlined.LinkOff,
        "DNS explained for beginners." to Icons.Outlined.Public,
        "How does OAuth2 work?" to Icons.Outlined.VpnKey,
        "Container vs VM explained." to Icons.Outlined.DeveloperBoard,
        "Write a CLI tool in Go." to Icons.Outlined.Terminal,

        // --- Creative & Writing ---
        "Write a haiku about a robot falling in love." to Icons.Outlined.Favorite,
        "Draft a professional email declining a job offer." to Icons.Outlined.Mail,
        "Brainstorm 5 names for a coffee shop." to Icons.Outlined.LocalCafe,
        "Write opening paragraph for a mystery novel." to Icons.Outlined.MenuBook,
        "Create a 7-day trip itinerary for Japan." to Icons.Outlined.FlightTakeoff,
        "Suggest 10 catchy titles for a blog about AI." to Icons.Outlined.Tag,
        "Write a poem about the sea." to Icons.Outlined.Waves,
        "Write a short bedtime story." to Icons.Outlined.Bedtime,
        "Invent a concept album and tracklist." to Icons.Outlined.Album,
        "Write a 30-second commercial script." to Icons.Outlined.Videocam,
        "Write a cyberpunk city description." to Icons.Outlined.LocationCity,
        "Write a stand-up comedy bit." to Icons.Outlined.Mic,
        "Describe a haunted library." to Icons.Outlined.LocalLibrary,
        "Write a villain monologue." to Icons.Outlined.TheaterComedy,
        "Write a 100-word sci-fi story." to Icons.Outlined.Rocket,
        "Describe a dream in vivid detail." to Icons.Outlined.NightsStay,

        // --- Academic & Learning ---
        "Explain quantum entanglement simply." to Icons.Outlined.AutoAwesome,
        "Summarize the French Revolution causes." to Icons.Outlined.History,
        "Fusion vs fission difference." to Icons.Outlined.LocalFireDepartment,
        "How do airplanes fly?" to Icons.Outlined.Flight,
        "Explain photosynthesis." to Icons.Outlined.Forest,
        "Explain the immune system." to Icons.Outlined.Healing,
        "Explain relativity with examples." to Icons.Outlined.AutoAwesome,
        "Explain Schrödinger's cat." to Icons.Outlined.Pets,
        "Explain climate change evidence." to Icons.Outlined.Eco,
        "Explain time dilation." to Icons.Outlined.HourglassTop,
        "Explain gravity." to Icons.Outlined.Expand,
        "Explain dark matter." to Icons.Outlined.Star,
        "Explain plate tectonics." to Icons.Outlined.Landscape,
        "Explain evolution." to Icons.Outlined.Pets,

        // --- Productivity & Soft Skills ---
        "How to improve public speaking." to Icons.Outlined.RecordVoiceOver,
        "Template for a project proposal." to Icons.Outlined.Description,
        "Questions to ask in interviews." to Icons.Outlined.QuestionAnswer,
        "How to negotiate salary." to Icons.Outlined.AttachMoney,
        "Create a study schedule." to Icons.Outlined.Event,
        "Books for personal growth." to Icons.Outlined.MenuBook,
        "How to handle difficult coworkers." to Icons.Outlined.SentimentDissatisfied,
        "Draft a LinkedIn summary." to Icons.Outlined.Badge,
        "How to avoid procrastination." to Icons.Outlined.Alarm,
        "Time management tips." to Icons.Outlined.Schedule,
        "How to prevent burnout." to Icons.Outlined.SelfImprovement,
        "How to lead teams effectively." to Icons.Outlined.Groups,
        "How to run effective meetings." to Icons.Outlined.Groups3,
        "How to prioritize tasks." to Icons.Outlined.Sort,

        // --- Fun & Trivia ---
        "Tell me a programmer joke." to Icons.Outlined.TagFaces,
        "Fun facts about octopuses." to Icons.Outlined.BubbleChart,
        "Pick a superhero power." to Icons.Outlined.FlashOn,
        "Trivia quiz about 90s movies." to Icons.Outlined.Movie,
        "Rank Star Wars movies." to Icons.Outlined.Stars,
        "Give me a riddle." to Icons.Outlined.Help,
        "Bear vs shark: who wins?" to Icons.Outlined.Dangerous,
        "Invent a holiday." to Icons.Outlined.Celebration,
        "Perfect sandwich description." to Icons.Outlined.LunchDining,
        "Invent a board game." to Icons.Outlined.Extension,
        "Theme park ride idea." to Icons.Outlined.RollerSkating,
        "Make up a fake law of physics." to Icons.Outlined.Gavel,
        "Describe the future of humanity." to Icons.Outlined.Public
    )
}
