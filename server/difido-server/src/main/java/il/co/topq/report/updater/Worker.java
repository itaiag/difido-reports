package il.co.topq.report.updater;

import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import il.co.topq.difido.DateTimeConverter;

class Worker {

	private static final Logger log = LoggerFactory.getLogger(Worker.class);

	private static final String EXECUTION_METADATA_QUERY_TEMPLATE = "INSERT INTO EXECUTION_METADATA "
			+ "(ID, COMMENT , DATE , DESCRIPTION , DURATION , FOLDER_NAME , NUM_OF_FAILED_TESTS , NUM_OF_MACHINES , NUM_OF_SUCCESSFUL_TESTS , NUM_OF_TESTS , NUM_OF_TESTS_WITH_WARNINGS , SHARED , TIME , TIMESTAMP , URI )"
			+ " VALUES ( ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";

	private static final String EXECUTION_STATE_QUERY_TEMPLATE = "INSERT INTO EXECUTION_STATE "
			+ "(METADATA_ID, ACTIVE , HTML_EXISTS , LOCKED ) VALUES (?,?,?,?);";

	private static final String EXECUTION_PROPERTIES_QUERY_TEMPLATE = "INSERT INTO EXECUTION_PROPERTIES "
			+ "(EXECUTION_ID, NAME , VALUE ) VALUES (?,?,?);";

	// @formatter:on

	private final JdbcTemplate template;
	private final Map<Integer, OldMetadata> data;
	private final boolean parallel;

	Worker(JdbcTemplate template, Map<Integer, OldMetadata> data, boolean parallel) {
		super();
		this.template = template;
		this.data = data;
		this.parallel = parallel;
	}

	void work() {
		if (null == data || data.isEmpty()) {
			log.warn("No data found");
			return;
		}
		Stream<OldMetadata> stream = null;
		if (parallel) {
			stream = data.values().parallelStream();
		} else {
			stream = data.values().stream();
		}
		stream.forEach(e -> {
			log.info("Working on execution " + e.getId());

			// @formatter:off
			template.update(EXECUTION_METADATA_QUERY_TEMPLATE, 
					Integer.toString(e.getId()),
					e.getComment(),
					DateTimeConverter.fromDateString(e.getDate()).toDateObject(),
					e.getDescription(),
					Long.toString(e.getDuration()),
					e.getFolderName(),
					Integer.toString(e.getNumOfFailedTests()),
					Integer.toString(e.getNumOfMachines()),
					Integer.toString(e.getNumOfSuccessfulTests()),
					Integer.toString(e.getNumOfTests()),
					Integer.toString(e.getNumOfTestsWithWarnings()),					
					e.isShared(),
					DateTimeConverter.fromTimeString(e.getTime()).toDateObject(),
					DateTimeConverter.fromElasticString(e.getTimestamp()).toDateObject(),
					e.getUri()
					);
			
			// @formatter:on
			Stream<String> keyStream = null;
			if (parallel) {
				keyStream = e.getProperties().keySet().parallelStream();
			} else {
				keyStream = e.getProperties().keySet().stream();
			}

			keyStream.forEach(key -> {
				template.update(EXECUTION_PROPERTIES_QUERY_TEMPLATE, e.getId(), key, e.getProperties().get(key));
			});

			template.update(EXECUTION_STATE_QUERY_TEMPLATE, e.getId(), e.isActive(), e.isHtmlExists(), e.isLocked());
		});
	}

}
