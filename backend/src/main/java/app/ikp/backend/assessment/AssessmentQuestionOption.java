package app.ikp.backend.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One MCQ option. is_correct is server-only data — never serialised to public payloads. */
@Entity
@Table(name = "assessment_question_options")
public class AssessmentQuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private AssessmentQuestion question;

    @Column(name = "option_text", nullable = false, columnDefinition = "text")
    private String optionText;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public Long getId() { return id; }
    public AssessmentQuestion getQuestion() { return question; }
    public void setQuestion(AssessmentQuestion question) { this.question = question; }
    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
